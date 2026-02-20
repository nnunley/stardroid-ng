package com.stardroid.awakening.layers

/**
 * Available rendering layers that can be toggled.
 */
enum class Layer(val displayName: String, val defaultVisible: Boolean) {
    STARS("Stars", true),
    CONSTELLATIONS("Constellations", true),
    SOLAR_SYSTEM("Solar System", true),
    GRID("Grid", false),
    HORIZON("Horizon", false),
    AR_CAMERA("AR Camera", false),
    MESSIER("Messier Objects", true),
    METEOR_SHOWERS("Meteor Showers", true),
    ECLIPTIC("Ecliptic", false),
    ISS("ISS", false),
    COMETS("Comets", false),
    SKY_GRADIENT("Sky Gradient", true),
    STAR_OF_BETHLEHEM("Star of Bethlehem", false)
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
