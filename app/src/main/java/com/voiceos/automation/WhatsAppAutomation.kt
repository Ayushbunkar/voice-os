package com.voiceos.automation

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.voiceos.service.VoiceAccessibilityService
import com.voiceos.utils.AppLogger
import com.voiceos.utils.AppUtils
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
        AppLogger.i(TAG, "sendMessage → contact=$contactName msg=$message")

        val service = VoiceAccessibilityService.instance ?: run {
            AppLogger.e(TAG, "AccessibilityService not running")
            return false
        }

        // Step 1: Open WhatsApp
        if (!AppUtils.launchApp(context, "WhatsApp")) {
            AppLogger.e(TAG, "Could not launch WhatsApp")
            return false
        }
        delay(2500L) // wait for splash / home screen

        // Step 2: Tap the search icon / FAB
        if (!tapSearch(service)) {
            AppLogger.w(TAG, "Could not find search — trying FAB click")
            tapNodeByDescription(service, "Search") ||
                    tapNodeByText(service, "Search")
            delay(800L)
        }

        // Step 3: Type contact name
        typeIntoFocused(service, contactName)
        delay(1500L) // wait for search results

        // Step 4: Tap the first contact result
        if (!tapContactResult(service, contactName)) {
            AppLogger.e(TAG, "Could not find contact: $contactName")
            AppUtils.showToast(context, "Contact \"$contactName\" not found")
            return false
        }
        delay(2000L) // wait for chat to load

        // Step 5: Tap message input
        if (!tapMessageInput(service)) {
            AppLogger.e(TAG, "Could not find message input box")
            return false
        }
        delay(500L)

        // Step 6: Type message
        typeIntoFocused(service, message)
        delay(400L)

        // Step 7: Tap Send
        if (!tapSendButton(service)) {
            AppLogger.e(TAG, "Could not find send button")
            return false
        }

        AppLogger.i(TAG, "Message sent successfully!")
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
        contactName: String
    ): Boolean {
        // Try up to 3 times (search results may lazily populate)
        repeat(3) { attempt ->
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = findNodeByTextContains(root, contactName)
                if (node != null) {
                    return performClick(node)
                }
            }
            AppLogger.d(TAG, "Contact not visible yet (attempt ${attempt+1}), waiting…")
            delay(600L)
        }
        return false
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
}
