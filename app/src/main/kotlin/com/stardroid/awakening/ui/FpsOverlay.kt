package com.stardroid.awakening.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

/**
 * Simple FPS counter overlay that can be toggled on/off.
 */
class FpsOverlay(context: Context) : TextView(context) {
    private var visible = true

    init {
        text = "FPS: --"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setBackgroundColor(Color.argb(128, 0, 0, 0))
        setPadding(16, 8, 16, 8)
        gravity = Gravity.CENTER
    }

    fun updateFps(fps: Double) {
        post {
            text = "FPS: %.1f".format(fps)
        }
    }

    fun toggle() {
        visible = !visible
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun show() {
        visible = true
        visibility = View.VISIBLE
    }

    fun hide() {
        visible = false
        visibility = View.GONE
    }

    val isShowing: Boolean
        get() = visible
}
