
package com.minato.graphics.outline

import java.awt.Color

data class OutlineStyle(
    val color: Color,
    val thickness: Float = 0.0005f,
    val glowIntensity: Float = 0.5f,
    val glowRadius: Float = 0.001f,
    val fill: Boolean = true,
    val fillOpacity: Float = 0.4f
) {
    companion object {
        val DEFAULT = OutlineStyle(Color.WHITE)
    }
}
