package com.voiceos.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.voiceos.utils.AppLogger

/**
 * OverlayManager — Optimized for high visibility.
 * Draws circular, high-contrast number badges over clickable elements.
 */
class OverlayManager(private val context: Context) {

    private val TAG = "OverlayManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayViews = mutableListOf<TextView>()

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
            
            // Adjust size to be circular (aspect ratio 1:1)
            val size = Math.max(badge.measuredWidth, badge.measuredHeight).coerceAtLeast(dpToPx(24))
            
            val (x, y) = centeredPosition(rect, size, size)
            val params = buildLayoutParams(x, y, size, size)
            
            try {
                if (badge.parent == null) {
                    windowManager.addView(badge, params)
                } else {
                    windowManager.updateViewLayout(badge, params)
                }
                badge.visibility = View.VISIBLE
                visibleCount++
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed overlay index $index", e)
            }
        }

        for (i in visibleCount until overlayViews.size) {
            overlayViews[i].visibility = View.GONE
        }
    }

    fun clearOverlay() {
        overlayViews.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to remove overlay", e)
            }
        }
        overlayViews.clear()
    }

    private fun ensureOverlayCount(target: Int) {
        while (overlayViews.size < target) {
            overlayViews.add(createBadge())
        }
    }

    private fun centeredPosition(rect: Rect, bW: Int, bH: Int): Pair<Int, Int> {
        return (rect.centerX() - bW / 2) to (rect.centerY() - bH / 2)
    }

    private fun createBadge(): TextView {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF2196F3.toInt()) // Vibrant Material Blue
            setStroke(dpToPx(2), Color.WHITE) // White border for contrast
        }

        return TextView(context).apply {
            textSize = 14f // Larger text
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = shape
            gravity = Gravity.CENTER
            elevation = 15f
        }
    }

    private fun buildLayoutParams(x: Int, y: Int, w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            w, h, type,
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

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
