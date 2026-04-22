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
        private const val DEFAULT_DEBOUNCE_MS = 240L
        private const val MAX_CLICKABLE_NODES = 100

        @Volatile
        var instance: VoiceAccessibilityService? = null
            private set
    }

    /** 1-based index → node map for the current window. */
    private val clickableNodes = mutableMapOf<Int, AccessibilityNodeInfo>()

    private lateinit var overlayManager: OverlayManager
    private var refreshDebounceMs = DEFAULT_DEBOUNCE_MS
    private var lastRefreshTime = 0L
    private var lastOverlaySignature = ""
    private var screenBounds = Rect()
    private var maxTargetAreaPx: Long = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = OverlayManager(this)
        val tuning = RuntimeTuning.get(applicationContext)
        refreshDebounceMs = tuning.accessibilityDebounceMs
        val dm = resources.displayMetrics
        screenBounds = Rect(0, 0, dm.widthPixels, dm.heightPixels)
        maxTargetAreaPx = (screenBounds.width().toLong() * screenBounds.height().toLong() * 85L) / 100L
        PerfMetrics.recordTuning(accessibilityDebounceMs = refreshDebounceMs)
        AppLogger.i(TAG, "Accessibility Service connected")
        AppLogger.i(TAG, "Using adaptive debounce=${refreshDebounceMs}ms (tier=${tuning.tier})")
    }

    override fun onDestroy() {
        clickableNodes.values.forEach { it.recycle() }
        clickableNodes.clear()
        overlayManager.clearOverlay()
        lastOverlaySignature = ""
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
                if (now - lastRefreshTime > refreshDebounceMs) {
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
        // Clear previous nodes and recycle them to prevent memory leaks
        clickableNodes.values.forEach { it.recycle() }
        clickableNodes.clear()

        val root = rootInActiveWindow
        if (root == null) {
            if (lastOverlaySignature.isNotEmpty()) {
                overlayManager.drawOverlay(emptyMap())
                lastOverlaySignature = ""
            }
            return
        }

        val collected = ArrayList<AccessibilityNodeInfo>(MAX_CLICKABLE_NODES)
        val seenNodeKeys = HashSet<String>(MAX_CLICKABLE_NODES * 2)
        collectUsableNodes(root, collected, seenNodeKeys, MAX_CLICKABLE_NODES)

        collected.sortWith(
            compareBy<AccessibilityNodeInfo> { nodeTop(it) }
                .thenBy { nodeLeft(it) }
        )

        collected.forEachIndexed { idx, node ->
            clickableNodes[idx + 1] = node
        }

        val signature = collected.joinToString(separator = "|") { buildNodeKey(it) }
        if (signature == lastOverlaySignature) {
            AppLogger.v(TAG, "Overlay unchanged (${clickableNodes.size} targets)")
            return
        }

        lastOverlaySignature = signature
        AppLogger.d(TAG, "Indexed ${clickableNodes.size} clickable elements")
        overlayManager.drawOverlay(clickableNodes)
    }

    private fun collectUsableNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<AccessibilityNodeInfo>,
        seenNodeKeys: MutableSet<String>,
        limit: Int
    ) {
        if (output.size >= limit) return

        val targetNode = resolveActionTarget(node)
        if (targetNode != null) {
            val nodeKey = buildNodeKey(targetNode)
            if (seenNodeKeys.add(nodeKey)) {
                output.add(targetNode)
                if (output.size >= limit) return
            } else {
                targetNode.recycle()
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectUsableNodes(child, output, seenNodeKeys, limit)
                child.recycle() // Crucial: recycle children after use
                if (output.size >= limit) return
            }
        }
    }

    private fun resolveActionTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser || !node.isEnabled) return null

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!isTargetRectUsable(rect)) return null

        if (node.isClickable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        val hasReadableText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        if (!hasReadableText || node.childCount > 0) {
            return null
        }

        val parent = node.parent
        var current = parent
        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                val targetRect = Rect()
                current.getBoundsInScreen(targetRect)
                val target = if (isTargetRectUsable(targetRect)) AccessibilityNodeInfo.obtain(current) else null
                current.recycle()
                return target
            }
            val next = current.parent
            current.recycle()
            current = next
        }

        return null
    }

    private fun isTargetRectUsable(rect: Rect): Boolean {
        if (rect.isEmpty || rect.width() < 5 || rect.height() < 5) return false
        if (!Rect.intersects(rect, screenBounds)) return false

        val clipped = Rect(rect)
        if (!clipped.intersect(screenBounds)) return false
        val area = clipped.width().toLong() * clipped.height().toLong()
        if (area <= 0L) return false
        return area <= maxTargetAreaPx
    }

    private fun buildNodeKey(node: AccessibilityNodeInfo): String {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val className = node.className?.toString().orEmpty()
        return "$className:${rect.left}:${rect.top}:${rect.right}:${rect.bottom}"
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.top
    }

    private fun nodeLeft(node: AccessibilityNodeInfo): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.left
    }

    // ── Public actions ────────────────────────────────────────────────

    fun clickElementAtIndex(index: Int): Boolean {
        val node = clickableNodes[index] ?: run {
            AppLogger.w(TAG, "No node at index $index")
            return false
        }
        
        AppLogger.i(TAG, "Clicking node #$index: ${node.className}")

        // 1. Try ACTION_CLICK on the node itself
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AppLogger.d(TAG, "Successfully performed ACTION_CLICK on node")
            return true
        }

        // 2. Try ACTION_CLICK on clickable ancestors
        var current = node.parent
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    AppLogger.d(TAG, "Successfully performed ACTION_CLICK on ancestor ${current.className}")
                    current.recycle()
                    return true
                }
            }
            val next = current.parent
            current.recycle()
            current = next
        }

        // 3. Fallback: Dispatch a touch gesture at the center of the node
        val rect = Rect()
        node.getBoundsInScreen(rect)
        AppLogger.d(TAG, "Falling back to gesture at ${rect.centerX()}, ${rect.centerY()}")
        return performTapGesture(rect.exactCenterX(), rect.exactCenterY())
    }

    fun performScroll(direction: Command.ScrollDirection) {
        AppLogger.i(TAG, "Scroll: $direction")
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (direction) {
            Command.ScrollDirection.DOWN -> listOf(w / 2, h * 0.74f, w / 2, h * 0.26f)
            Command.ScrollDirection.UP   -> listOf(w / 2, h * 0.26f, w / 2, h * 0.74f)
            Command.ScrollDirection.LEFT -> listOf(w * 0.82f, h / 2, w * 0.18f, h / 2)
            Command.ScrollDirection.RIGHT -> listOf(w * 0.18f, h / 2, w * 0.82f, h / 2)
        }
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val durationMs = when (direction) {
            Command.ScrollDirection.LEFT, Command.ScrollDirection.RIGHT -> 260L
            else -> 300L
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
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
        editable.recycle()
        AppLogger.d(TAG, "typeTextIntoFocused(\"$text\") = $result")
        return result
    }

    // ── Private helpers ───────────────────────────────────────────────

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditable(child)
            child.recycle()
            if (found != null) return found
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
