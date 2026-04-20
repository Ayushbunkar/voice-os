package com.voiceos.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.voiceos.model.Command
import com.voiceos.overlay.OverlayManager
import com.voiceos.utils.AppLogger

/**
 * VoiceAccessibilityService — Phase 2 upgraded core engine.
 *
 * New in Phase 2:
 *   • [typeTextIntoFocused] — types arbitrary text into the focused field
 *   • [findFocusedEditable]  — exposes editable-node finder to WhatsApp automation
 *   • Overlay refresh is now debounced (avoids redundant redraws on rapid events)
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val DEBOUNCE_MS = 300L

        @Volatile
        var instance: VoiceAccessibilityService? = null
            private set
    }

    /** 1-based index → node map for the current window. */
    private val clickableNodes = mutableMapOf<Int, AccessibilityNodeInfo>()

    private lateinit var overlayManager: OverlayManager
    private var lastRefreshTime = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = OverlayManager(this)
        AppLogger.i(TAG, "Accessibility Service connected")
    }

    override fun onDestroy() {
        overlayManager.clearOverlay()
        instance = null
        AppLogger.i(TAG, "Accessibility Service destroyed")
        super.onDestroy()
    }

    // ── Events ────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastRefreshTime > DEBOUNCE_MS) {
                    lastRefreshTime = now
                    refreshClickableNodes()
                }
            }
        }
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Accessibility Service interrupted")
    }

    // ── Node traversal ────────────────────────────────────────────────

    private fun refreshClickableNodes() {
        clickableNodes.clear()
        val root = rootInActiveWindow ?: return
        var index = 1
        traverseTree(root) { node ->
            clickableNodes[index++] = AccessibilityNodeInfo.obtain(node)
        }
        AppLogger.d(TAG, "Indexed ${clickableNodes.size} clickable elements")
        overlayManager.drawOverlay(clickableNodes)
    }

    private fun traverseTree(
        node: AccessibilityNodeInfo,
        collector: (AccessibilityNodeInfo) -> Unit
    ) {
        if (isUsableNode(node)) collector(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverseTree(it, collector) }
        }
    }

    private fun isUsableNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable || !node.isVisibleToUser) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return !rect.isEmpty
    }

    // ── Public actions ────────────────────────────────────────────────

    fun clickElementAtIndex(index: Int): Boolean {
        val node = clickableNodes[index] ?: run {
            AppLogger.w(TAG, "No node at index $index")
            return false
        }
        AppLogger.i(TAG, "Clicking node #$index: ${node.className}")
        return if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            true
        } else {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            performTapGesture(rect.exactCenterX(), rect.exactCenterY())
        }
    }

    fun performScroll(direction: Command.ScrollDirection) {
        AppLogger.i(TAG, "Scroll: $direction")
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (direction) {
            Command.ScrollDirection.DOWN -> listOf(w / 2, h * 0.72f, w / 2, h * 0.28f)
            Command.ScrollDirection.UP   -> listOf(w / 2, h * 0.28f, w / 2, h * 0.72f)
        }
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 350L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun performBack() {
        AppLogger.i(TAG, "GLOBAL_ACTION_BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Phase 2: Types [text] into the currently focused editable field.
     * Uses ACTION_SET_TEXT which works across all modern Android versions.
     */
    fun typeTextIntoFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findFocusedEditable(root) ?: run {
            AppLogger.w(TAG, "No focused editable node found for typeText")
            return false
        }
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        val result = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        AppLogger.d(TAG, "typeTextIntoFocused(\"$text\") = $result")
        return result
    }

    // ── Private helpers ───────────────────────────────────────────────

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findFocusedEditable(it) }?.let { return it }
        }
        return null
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(), null, null
        )
    }
}
