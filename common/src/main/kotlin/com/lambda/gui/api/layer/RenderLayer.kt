package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.RectRenderer
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d

class RenderLayer {
    var allowEffects = false

    private val rect = RectRenderer()
    private val font = FontRenderer()

    fun entry() = LayerEntry(rect, font)

    fun assignOffset(ofs: Vec2d) {
        rect.matrixOffset = ofs
        font.matrixOffset = ofs
    }

    fun render() {
        rect.update()
        font.update()

        rect.apply {
            shadeColor = GuiSettings.shade && allowEffects
            fancyBlending = GuiSettings.glow && allowEffects
            render()
        }

        font.render()
    }

    fun destroy() {
        rect.destroy()
        font.destroy()
    }
}
