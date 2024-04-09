package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.RectRenderer
import com.lambda.module.modules.client.ClickGui
import com.mojang.blaze3d.systems.RenderSystem.blendFunc
import com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA

class RenderLayer(private val allowEffects: Boolean = false) {
    private val rectRenderer = RectRenderer()

    val rect = rectRenderer.asRenderer
    val font = FontRenderer().asRenderer

    fun render() {
        rect.update()
        font.update()

        rectRenderer.shadeColor = ClickGui.shade
        applyFancyBlending(rect::render)

        font.render()
    }

    fun destroy() {
        rect.destroy()
        font.destroy()
    }

    private fun applyFancyBlending(block: () -> Unit) {
        if (ClickGui.glow && allowEffects) {
            blendFunc(GL_SRC_ALPHA, GL_ONE)
            block()
            defaultBlendFunc()
        } else block()
    }
}
