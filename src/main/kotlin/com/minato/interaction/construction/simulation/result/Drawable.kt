
package com.minato.interaction.construction.simulation.result

import com.minato.graphics.mc.RenderBuilder

/**
 * Represents a [BuildResult] that can be rendered in-game.
 */
interface Drawable {
    fun RenderBuilder.render()
}
