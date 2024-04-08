package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.RectRenderer
import com.lambda.module.modules.client.ClickGui
import com.mojang.blaze3d.systems.RenderSystem.blendFunc
import com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA

class RenderLayer(private val allowGlowing: Boolean = false) {
    val rect = RectRenderer().asRenderer
    val font = FontRenderer().asRenderer

    fun render() {
        rect.update()
        font.update()

        if (allowGlowing && ClickGui.glow) blendFunc(GL_SRC_ALPHA, GL_ONE)
        rect.render()
        defaultBlendFunc()

        font.render()
    }

    fun destroy() {
        rect.destroy()
        font.destroy()
    }
}
