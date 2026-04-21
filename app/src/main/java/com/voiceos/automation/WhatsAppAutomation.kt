package com.voiceos.automation

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.voiceos.service.VoiceAccessibilityService
import com.voiceos.utils.AppLogger
import com.voiceos.utils.AppUtils
import com.voiceos.utils.PerfMetrics
import com.voiceos.utils.RuntimeTuningProfile
import com.voiceos.utils.RuntimeTuning
import kotlinx.coroutines.delay

/**
 * WhatsAppAutomation — Drives WhatsApp via the AccessibilityService.
 *
 * Executes the following pipeline:
 *   1. Launch WhatsApp
 *   2. Wait for the app to load
 *   3. Tap the search icon
 *   4. Type the contact name
 *   5. Tap the first matching result
 *   6. Wait for chat screen to load
 *   7. Tap the message input box
 *   8. Type the message
 *   9. Tap the Send button
 *
 * All node lookups have fallback logic (by text, content-description, viewId,
 * and resource-id patterns) so it works across WhatsApp versions.
 */
object WhatsAppAutomation {

    private const val TAG = "WhatsAppAutomation"
    const val PACKAGE = "com.whatsapp"

    /**
     * Send [message] to [contactName] through WhatsApp.
     * Call from a coroutine – uses [delay] between steps to let UI settle.
     *
     * @return true on apparent success, false if a step failed.
     */
    suspend fun sendMessage(
        contactName: String,
        message: String,
        context: android.content.Context
    ): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val tuning = RuntimeTuning.get(context)
        val pollMs = tuning.whatsAppPollMs
        PerfMetrics.recordTuning(whatsAppPollMs = pollMs)

        AppLogger.i(TAG, "sendMessage → contact=$contactName msg=$message")
        AppLogger.i(
            TAG,
            "Using adaptive WhatsApp polling=${pollMs}ms timeoutScale=${tuning.timeoutScale} tier=${tuning.tier}"
        )

        fun fail(reason: String): Boolean {
            val totalMs = SystemClock.elapsedRealtime() - startedAt
            PerfMetrics.recordWhatsAppTotal(totalMs, success = false)
            AppLogger.w(TAG, "sendMessage failed reason=$reason latency=${totalMs}ms")
            return false
        }

        suspend fun runStep(stepName: String, action: suspend () -> Boolean): Boolean {
            val stepStart = SystemClock.elapsedRealtime()
            val success = runCatching { action() }.getOrElse {
                AppLogger.e(TAG, "Step failed with exception: $stepName", it)
                false
            }
            val stepMs = SystemClock.elapsedRealtime() - stepStart
            PerfMetrics.recordWhatsAppStep(stepName, stepMs, success)
            AppLogger.i(TAG, "step=$stepName latency=${stepMs}ms success=$success")
            return success
        }

        val service = VoiceAccessibilityService.instance ?: run {
            AppLogger.e(TAG, "AccessibilityService not running")
            return fail("service_not_running")
        }

        // Step 1: Open WhatsApp
        if (!runStep("launch_whatsapp") { AppUtils.launchApp(context, "WhatsApp") }) {
            AppLogger.e(TAG, "Could not launch WhatsApp")
            return fail("launch_failed")
        }

        val rootReady = runStep("wait_root_window") {
            waitUntil(
                timeoutMs = RuntimeTuning.scaleTimeout(3500L, tuning),
                pollMs = pollMs
            ) { service.rootInActiveWindow != null }
        }
        if (!rootReady) return fail("window_not_ready")

        // Step 2: Tap the search icon / FAB
        val searchFound = runStep("tap_search") {
            waitUntil(
                timeoutMs = RuntimeTuning.scaleTimeout(2500L, tuning),
                pollMs = pollMs
            ) {
                tapSearch(service) || tapNodeByDescription(service, "Search") || tapNodeByText(service, "Search")
            }
        }
        if (!searchFound) {
            AppLogger.w(TAG, "Could not find search explicitly — trying focused input fallback")
        }

        // Step 3: Type contact name
        val contactTyped = runStep("type_contact_name") { typeIntoFocused(service, contactName) }
        if (!contactTyped) {
            AppLogger.e(TAG, "Could not type contact name into focused field")
            return fail("type_contact_failed")
        }

        // Step 4: Tap the first contact result
        if (!runStep("select_contact") { tapContactResult(service, contactName, pollMs, tuning) }) {
            AppLogger.e(TAG, "Could not find contact: $contactName")
            AppUtils.showToast(context, "Contact \"$contactName\" not found")
            return fail("contact_not_found")
        }

        // Step 5: Tap message input
        val inputFocused = runStep("focus_message_input") {
            waitUntil(
                timeoutMs = RuntimeTuning.scaleTimeout(3500L, tuning),
                pollMs = pollMs
            ) { tapMessageInput(service) }
        }
        if (!inputFocused) {
            AppLogger.e(TAG, "Could not find message input box")
            return fail("message_input_not_found")
        }

        // Step 6: Type message
        val messageTyped = runStep("type_message") { typeIntoFocused(service, message) }
        if (!messageTyped) {
            AppLogger.e(TAG, "Could not type message")
            return fail("type_message_failed")
        }
        delay(120L)

        // Step 7: Tap Send
        val sent = runStep("tap_send") {
            waitUntil(
                timeoutMs = RuntimeTuning.scaleTimeout(1500L, tuning),
                pollMs = pollMs
            ) { tapSendButton(service) }
        }
        if (!sent) {
            AppLogger.e(TAG, "Could not find send button")
            return fail("send_button_not_found")
        }

        val totalMs = SystemClock.elapsedRealtime() - startedAt
        PerfMetrics.recordWhatsAppTotal(totalMs, success = true)
        AppLogger.i(TAG, "Message sent successfully!")
        AppLogger.i(TAG, "sendMessage latency=${totalMs}ms success=true")
        return true
    }

    // ── Step implementations ──────────────────────────────────────────

    private fun tapSearch(service: VoiceAccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        // Try by content-description first (most reliable across WA versions)
        val node = findNodeByDescription(root, "Search") ?:
                   findNodeByResourceIdContains(root, "search") ?:
                   findNodeByText(root, "Search")
        return node?.let { performClick(it) } ?: false
    }

    private suspend fun tapContactResult(
        service: VoiceAccessibilityService,
        contactName: String,
        pollMs: Long,
        tuning: RuntimeTuningProfile
    ): Boolean {
        var attempt = 0
        val found = waitUntil(
            timeoutMs = RuntimeTuning.scaleTimeout(3500L, tuning),
            pollMs = pollMs
        ) {
            attempt += 1
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = findNodeByTextContains(root, contactName)
                if (node != null) {
                    return@waitUntil performClick(node)
                }
            }
            if (attempt % 5 == 0) {
                AppLogger.d(TAG, "Contact not visible yet (attempt $attempt)")
            }
            false
        }
        return found
    }

    private fun tapMessageInput(service: VoiceAccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByResourceIdContains(root, "entry") ?:
                   findNodeByResourceIdContains(root, "message") ?:
                   findNodeByDescription(root, "Message") ?:
                   findNodeByDescription(root, "Type a message")
        return node?.let { n ->
            performClick(n)
        } ?: false
    }

    private fun tapSendButton(service: VoiceAccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByDescription(root, "Send") ?:
                   findNodeByResourceIdContains(root, "send")
        return node?.let { performClick(it) } ?: false
    }

    private fun tapNodeByDescription(service: VoiceAccessibilityService, desc: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return findNodeByDescription(root, desc)?.let { performClick(it) } ?: false
    }

    private fun tapNodeByText(service: VoiceAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return findNodeByText(root, text)?.let { performClick(it) } ?: false
    }

    // ── Text input ────────────────────────────────────────────────────

    /**
     * Types [text] into whichever node currently has focus using
     * ACTION_SET_TEXT (API 21+). Falls back to individual character events if needed.
     */
    private fun typeIntoFocused(service: VoiceAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val focused = findFocusedOrEditable(root) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        AppLogger.d(TAG, "typeIntoFocused(\"$text\") = $result")
        return result
    }

    // ── Node search utilities ─────────────────────────────────────────

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.equals(text, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findNodeByText(child, text)?.let { return it }
            }
        }
        return null
    }

    private fun findNodeByTextContains(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = root.text?.toString() ?: root.contentDescription?.toString() ?: ""
        if (nodeText.contains(text, ignoreCase = true)) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findNodeByTextContains(child, text)?.let { return it }
            }
        }
        return null
    }

    private fun findNodeByDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findNodeByDescription(child, desc)?.let { return it }
            }
        }
        return null
    }

    private fun findNodeByResourceIdContains(root: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        val id = root.viewIdResourceName ?: ""
        if (id.contains(idPart, ignoreCase = true)) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findNodeByResourceIdContains(child, idPart)?.let { return it }
            }
        }
        return null
    }

    private fun findFocusedOrEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && root.isEditable) return root
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findFocusedOrEditable(child)?.let { return it }
            }
        }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Walk up to find clickable parent
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
            false
        }
    }

    private suspend fun waitUntil(
        timeoutMs: Long,
        pollMs: Long,
        condition: () -> Boolean
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            delay(pollMs)
        }
        return condition()
    }
}
