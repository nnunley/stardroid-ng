package com.stardroid.awakening.layers

/**
 * Available rendering layers that can be toggled.
 */
enum class Layer(val displayName: String, val defaultVisible: Boolean) {
    STARS("Stars", true),
    CONSTELLATIONS("Constellations", true),
    GRID("Grid", false),
    HORIZON("Horizon", false),
    AR_CAMERA("AR Camera", false)
}

/**
 * Manages layer visibility state.
 */
class LayerManager {
    private val visibilityState = mutableMapOf<Layer, Boolean>()

    init {
        // Initialize with defaults
        Layer.entries.forEach { layer ->
            visibilityState[layer] = layer.defaultVisible
        }
    }

    fun isVisible(layer: Layer): Boolean {
        return visibilityState[layer] ?: layer.defaultVisible
    }

    fun setVisible(layer: Layer, visible: Boolean) {
        visibilityState[layer] = visible
    }

    fun toggle(layer: Layer) {
        visibilityState[layer] = !isVisible(layer)
    }

    fun getVisibleLayers(): Set<Layer> {
        return visibilityState.filter { it.value }.keys
    }
}
