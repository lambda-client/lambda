package com.minato.graphics.hud

import com.minato.Minato
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.RenderDsl
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer

object HudRenderRegistry {
    data class HudEntry(
        val name: String,
        var x: Float,
        var y: Float,
        var w: Float,
        var h: Float,
        val renderer: RenderBuilder.() -> Unit
    )

    private val entries = mutableMapOf<String, HudEntry>()

    fun update(name: String, x: Float, y: Float, w: Float, h: Float, renderer: RenderBuilder.() -> Unit) {
        entries[name] = HudEntry(name, x, y, w, h, renderer)
    }

    fun remove(name: String) {
        entries.remove(name)
    }

    fun clear() = entries.clear()

    fun getEntries() = entries.values.toList()

    init {
        // register an immediate renderer that draws HUD entries each frame in render pipeline
        immediateRenderer("HUD Render", depthTest = { false }) {
            // draw all registered HUD visuals
            entries.values.forEach { entry ->
                entry.renderer(this)
            }
        }
    }
}
