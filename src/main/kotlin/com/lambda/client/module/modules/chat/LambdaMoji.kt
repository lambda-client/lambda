package com.lambda.client.module.modules.chat

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.manager.managers.LambdaMojiManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.texture.MipmapTexture
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11.*

object LambdaMoji : Module(
    name = "LambdaMoji",
    description = "Add emojis to chat using LambdaMoji, courtesy of the EmojiAPI",
    category = Category.CHAT
) {
    @JvmStatic
    fun renderText(inputText: String, fontHeight: Int, shadow: Boolean, posX: Float, posY: Float, alpha: Float): String {
        var text = inputText
        val blend = glGetBoolean(GL_BLEND)

        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha)
        GlStateUtils.blend(true)
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE)

        for (possible in text.split(":")) {
            val texture = LambdaMojiManager.getEmoji(possible) ?: continue
            val emojiText = ":$possible:"

            if (!shadow) {
                val index = text.indexOf(emojiText)
                if (index == -1) continue

                val x = mc.fontRenderer.getStringWidth(text.substring(0, index)) + fontHeight / 4
                drawEmoji(texture, (posX + x).toDouble(), posY.toDouble(), fontHeight.toFloat())
            }

            text = text.replaceFirst(emojiText, getReplacement(fontHeight))
        }

        GlStateUtils.blend(blend)
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        return text
    }

    @JvmStatic
    fun getStringWidth(inputWidth: Int, inputText: String, fontHeight: Int): Int {
        var text = inputText
        var reducedWidth = inputWidth

        for (possible in text.split(":")) {
            if (LambdaMojiManager.isEmoji(possible)) {
                val emojiText = ":$possible:"
                val emojiTextWidth = emojiText.sumOf { mc.fontRenderer.getCharWidth(it) }
                reducedWidth -= emojiTextWidth
                text = text.replaceFirst(emojiText, getReplacement(fontHeight))
            }
        }

        return reducedWidth
    }

    private fun getReplacement(fontHeight: Int): String {
        val emojiWidth = (fontHeight / mc.fontRenderer.getCharWidth(' ').toDouble()).ceilToInt()
        val spaces = CharArray(emojiWidth) { ' ' }
        return String(spaces)
    }

    /* This is created because vanilla one doesn't take double position input */
    private fun drawEmoji(texture: MipmapTexture, x: Double, y: Double, size: Float) {
        val tessellator = Tessellator.getInstance()
        val bufBuilder = tessellator.buffer

        texture.bindTexture()

        bufBuilder.begin(GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX)
        bufBuilder.pos(x, y + size, 0.0).tex(0.0, 1.0).endVertex()
        bufBuilder.pos(x + size, y + size, 0.0).tex(1.0, 1.0).endVertex()
        bufBuilder.pos(x, y, 0.0).tex(0.0, 0.0).endVertex()
        bufBuilder.pos(x + size, y, 0.0).tex(1.0, 0.0).endVertex()
        tessellator.draw()
    }
}
