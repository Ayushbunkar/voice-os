package com.voiceos.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.voiceos.utils.AppLogger

/**
 * OverlayManager — Responsible for drawing numbered labels on top of ALL apps.
 *
 * Behaviour:
 *  • For every clickable node provided, creates a small [TextView] badge positioned
 *    at the center of that node's screen bounding rect.
 *  • All views are added to [WindowManager] with TYPE_ACCESSIBILITY_OVERLAY so they
 *    appear above the target app without requiring SYSTEM_ALERT_WINDOW when running
 *    inside an AccessibilityService.
 *
 * Call [drawOverlay] to refresh and [clearOverlay] to remove all badges.
 */
class OverlayManager(private val context: Context) {

    private val TAG = "OverlayManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Reused overlay badge pool to avoid add/remove view churn. */
    private val overlayViews = mutableListOf<TextView>()

    /**
     * Removes all existing labels then draws a fresh badge for each entry in [nodes].
     * The map key is the 1-based index shown to the user.
     */
    fun drawOverlay(nodes: Map<Int, AccessibilityNodeInfo>) {
        val sorted = nodes.toSortedMap()
        ensureOverlayCount(sorted.size)

        var visibleCount = 0
        sorted.forEach { (index, node) ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.isEmpty) return@forEach

            val badge = overlayViews[visibleCount]
            badge.text = index.toString()
            badge.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val (x, y) = centeredPosition(
                rect = rect,
                badgeWidth = badge.measuredWidth,
                badgeHeight = badge.measuredHeight
            )
            val params = buildLayoutParams(x, y)
            try {
                if (badge.parent == null) {
                    windowManager.addView(badge, params)
                } else {
                    windowManager.updateViewLayout(badge, params)
                }
                badge.visibility = View.VISIBLE
                visibleCount++
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add overlay view for index $index", e)
            }
        }

        for (i in visibleCount until overlayViews.size) {
            overlayViews[i].visibility = View.GONE
        }

        AppLogger.d(TAG, "Overlay drawn with $visibleCount badges")
    }

    /** Remove all overlay badges from the screen immediately. */
    fun clearOverlay() {
        overlayViews.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayViews.clear()
        AppLogger.d(TAG, "Overlay cleared")
    }

    private fun ensureOverlayCount(target: Int) {
        while (overlayViews.size < target) {
            overlayViews.add(createBadge(overlayViews.size + 1))
        }
    }

    private fun centeredPosition(rect: Rect, badgeWidth: Int, badgeHeight: Int): Pair<Int, Int> {
        val safeWidth = badgeWidth.coerceAtLeast(1)
        val safeHeight = badgeHeight.coerceAtLeast(1)
        val x = (rect.centerX() - safeWidth / 2).coerceAtLeast(0)
        val y = (rect.centerY() - safeHeight / 2).coerceAtLeast(0)
        return x to y
    }

    // ── Private builders ──────────────────────────────────────────────────

    /** Creates a styled number badge TextView. */
    private fun createBadge(index: Int): TextView {
        return TextView(context).apply {
            text = index.toString()
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1A73E8.toInt()) // semi-transparent Google-blue
            setPadding(6, 2, 6, 2)
            gravity = Gravity.CENTER
            elevation = 10f
        }
    }

    /**
     * Builds [WindowManager.LayoutParams] so the badge sits at (x, y) on screen.
     * Uses TYPE_ACCESSIBILITY_OVERLAY which works inside AccessibilityService without
     * needing the SYSTEM_ALERT_WINDOW permission.
     */
    private fun buildLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }
}
