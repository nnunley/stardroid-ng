package com.stardroid.awakening.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.stardroid.awakening.layers.Layer
import com.stardroid.awakening.layers.LayerManager

/**
 * FAB button that opens a layer toggle dialog with opacity control.
 */
class LayerToggleOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private var layerManager: LayerManager? = null,
    private var onLayerChanged: (() -> Unit)? = null,
    private var onOpacityChanged: ((Float) -> Unit)? = null
) : FloatingActionButton(context, attrs) {

    private var currentOpacity: Float = 0f

    init {
        // Style the FAB
        setImageResource(android.R.drawable.ic_menu_manage)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.argb(200, 60, 60, 60))
        size = SIZE_MINI

        setOnClickListener { showLayerDialog() }
    }

    fun setup(manager: LayerManager, onChange: () -> Unit, onOpacity: (Float) -> Unit = {}) {
        layerManager = manager
        onLayerChanged = onChange
        onOpacityChanged = onOpacity
    }

    private fun showLayerDialog() {
        val manager = layerManager ?: return

        val layers = Layer.entries.toTypedArray()
        val layerNames = layers.map { it.displayName }.toTypedArray()
        val checkedItems = layers.map { manager.isVisible(it) }.toBooleanArray()

        // Create custom view with layers + opacity slider
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // Opacity label
        val opacityLabel = TextView(context).apply {
            text = "Background: ${(currentOpacity * 100).toInt()}%"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        layout.addView(opacityLabel)

        // Opacity slider
        val opacitySlider = SeekBar(context).apply {
            max = 100
            progress = (currentOpacity * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentOpacity = progress / 100f
                    opacityLabel.text = "Background: ${progress}%"
                    onOpacityChanged?.invoke(currentOpacity)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(opacitySlider)

        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Layers")
            .setView(layout)
            .setMultiChoiceItems(layerNames, checkedItems) { _, which, isChecked ->
                manager.setVisible(layers[which], isChecked)
                onLayerChanged?.invoke()
            }
            .setPositiveButton("Done", null)
            .show()
    }
}
