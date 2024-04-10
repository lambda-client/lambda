package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.RectRenderer
import com.lambda.module.modules.client.GuiSettings

class RenderLayer(private val allowEffects: Boolean = false) {
    private val rectRenderer = RectRenderer()

    val rect = rectRenderer.asRenderer
    val font = FontRenderer().asRenderer

    fun render() {
        rect.update()
        font.update()

        rectRenderer.apply {
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
