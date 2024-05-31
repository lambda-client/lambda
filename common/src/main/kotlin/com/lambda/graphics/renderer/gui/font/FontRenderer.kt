package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.Renderer
import com.lambda.graphics.renderer.gui.font.glyph.CharInfo
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import java.awt.Color

class FontRenderer(private val font: LambdaFont) : Renderer(VertexMode.TRIANGLES, VertexAttrib.Group.FONT) {
    var scaleMultiplier = 1.0

    fun build(
        text: String,
        position: Vec2d,
        color: Color = Color.WHITE,
        scale: Double = 1.0,
        shadow: Boolean = true
    ) = vao.use {
        val actualScale = getScaleFactor(scale)
        val scaledShadowShift = shadowShift * actualScale
        val scaledGap = gap * actualScale
        val shadowColor = getShadowColor(color)

        var posX = 0.0
        val posY = getHeight(scale) * -0.5 + baselineOffset * actualScale

        text.toCharArray().forEach { char ->
            val charInfo = font[char] ?: return@forEach
            val scaledSize = charInfo.size * actualScale

            val pos1 = Vec2d(posX, posY)
            val pos2 = pos1 + scaledSize

            if (shadow && FontSettings.shadow) {
                val shadowPos1 = pos1 + scaledShadowShift
                val shadowPos2 = shadowPos1 + scaledSize
                putChar(position, shadowPos1, shadowPos2, shadowColor, charInfo)
            }

            putChar(position, pos1, pos2, color, charInfo)

            posX += scaledSize.x + scaledGap
        }
    }

    fun getWidth(text: String, scale: Double = 1.0): Double {
        var width = 0.0

        text.forEach { char ->
            val glyph = font[char] ?: return@forEach
            width += glyph.size.x + gap
        }

        return width * getScaleFactor(scale)
    }

    fun getHeight(scale: Double = 1.0) =
        font.glyphs.fontHeight * getScaleFactor(scale) * 0.7

    private fun IRenderContext.putChar(pos: Vec2d, lt: Vec2d, rb: Vec2d, color: Color, ci: CharInfo) {
        val x = pos.x
        val y = pos.y

        grow(4)

        putQuad(
            vec2(lt.x + x, lt.y + y).vec2(ci.uv1.x, ci.uv1.y).color(color).end(),
            vec2(lt.x + x, rb.y + y).vec2(ci.uv1.x, ci.uv2.y).color(color).end(),
            vec2(rb.x + x, rb.y + y).vec2(ci.uv2.x, ci.uv2.y).color(color).end(),
            vec2(rb.x + x, lt.y + y).vec2(ci.uv2.x, ci.uv1.y).color(color).end()
        )
    }

    private fun getScaleFactor(scale: Double) =
        scaleMultiplier * scale * 0.12

    private fun getShadowColor(color: Color) = Color(
        (color.red * FontSettings.shadowBrightness).toInt(),
        (color.green * FontSettings.shadowBrightness).toInt(),
        (color.blue * FontSettings.shadowBrightness).toInt(),
        color.alpha
    )

    override fun render() {
        shader.use()
        font.glyphs.bind()
        super.render()
    }

    companion object {
        private val shader = Shader("renderer/font")

        private val shadowShift get() = FontSettings.shadowShift * 4.0
        private val baselineOffset get() = FontSettings.baselineOffset * 2.0f - 10f
        private val gap get() = FontSettings.gapSetting * 0.5f - 0.8f
    }
}