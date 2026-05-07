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
import com.voiceos.utils.PerfMetrics
import com.voiceos.utils.RuntimeTuning

/**
 * VoiceAccessibilityService — Optimized for BLISTERING FAST response.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val MAX_CLICKABLE_NODES = 120 // Slightly increased limit

        @Volatile
        var instance: VoiceAccessibilityService? = null
            private set
    }

    private val clickableNodes = mutableMapOf<Int, AccessibilityNodeInfo>()
    private lateinit var overlayManager: OverlayManager
    private var lastRefreshTime = 0L
    private var lastOverlaySignature = ""
    private var screenBounds = Rect()
    private var maxTargetAreaPx: Long = 0L
    private var debounceMs: Long = 400L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = OverlayManager(this)
        
        val tuning = RuntimeTuning.get(this)
        debounceMs = tuning.accessibilityDebounceMs
        
        val dm = resources.displayMetrics
        screenBounds = Rect(0, 0, dm.widthPixels, dm.heightPixels)
        maxTargetAreaPx = (screenBounds.width().toLong() * screenBounds.height().toLong() * 90L) / 100L
        AppLogger.i(TAG, "Service Connected - Optimized Mode (debounce=${debounceMs}ms)")
    }

    override fun onDestroy() {
        clickableNodes.values.forEach { it.recycle() }
        clickableNodes.clear()
        overlayManager.clearOverlay()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only refresh on meaningful UI changes to save CPU
        val type = event.eventType
        val isMeaningful = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        if (!isMeaningful) return

        val now = System.currentTimeMillis()
        if (now - lastRefreshTime > debounceMs) {
            lastRefreshTime = now
            refreshClickableNodes()
        }
    }

    override fun onInterrupt() {}

    private fun refreshClickableNodes() {
        val root = rootInActiveWindow ?: return
        
        clickableNodes.values.forEach { it.recycle() }
        clickableNodes.clear()

        val collected = ArrayList<AccessibilityNodeInfo>(MAX_CLICKABLE_NODES)
        collectUsableNodes(root, collected, HashSet(), MAX_CLICKABLE_NODES)

        collected.forEachIndexed { idx, node ->
            clickableNodes[idx + 1] = node
        }

        // Draw overlay only if something changed significantly (count, package, or positions)
        val boundsHash = collected.fold(0) { acc, node ->
            val r = Rect()
            node.getBoundsInScreen(r)
            acc xor r.hashCode()
        }
        val signature = "${collected.size}_${root.packageName}_$boundsHash"
        
        if (signature != lastOverlaySignature) {
            lastOverlaySignature = signature
            overlayManager.drawOverlay(clickableNodes)
        }
    }

    private fun collectUsableNodes(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>, seen: MutableSet<String>, limit: Int) {
        if (output.size >= limit) return
        
        if (node.isVisibleToUser && (node.isClickable || node.isEditable)) {
            output.add(AccessibilityNodeInfo.obtain(node))
            if (output.size >= limit) return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectUsableNodes(child, output, seen, limit)
            child.recycle()
        }
    }

    // ── Public Actions (INSTANT RESPONSE) ───────────────────────────

    fun clickElementAtIndex(index: Int): Boolean {
        val node = clickableNodes[index] ?: return false
        
        // Instant priority 1: Native Click
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        
        // Instant priority 2: Parent Click
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                parent.recycle()
                return true
            }
            val next = parent.parent
            parent.recycle()
            parent = next
        }

        // Instant priority 3: Tap Gesture (Reduced to 10ms for instant feel)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return performTapGesture(rect.exactCenterX(), rect.exactCenterY())
    }

    fun typeTextIntoFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        // Ensure focused and clicked before typing
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        return result
    }

    fun performScroll(direction: Command.ScrollDirection) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path()
        when (direction) {
            Command.ScrollDirection.DOWN -> { path.moveTo(w/2, h*0.8f); path.lineTo(w/2, h*0.2f) }
            Command.ScrollDirection.UP -> { path.moveTo(w/2, h*0.2f); path.lineTo(w/2, h*0.8f) }
            Command.ScrollDirection.LEFT -> { path.moveTo(w*0.8f, h/2); path.lineTo(w*0.2f, h/2) }
            Command.ScrollDirection.RIGHT -> { path.moveTo(w*0.2f, h/2); path.lineTo(w*0.8f, h/2) }
        }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 200)).build(), null, null)
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        // 10ms duration is perceived as instantaneous
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 10)).build(), null, null)
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
}
