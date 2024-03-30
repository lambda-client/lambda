package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.gui.font.glyph.CharInfo
import com.lambda.module.modules.client.FontSettings
import com.lambda.util.math.Vec2d
import java.awt.Color

class FontEntry(
    override val owner: FontRenderer,
    override val updateBlock: IFontEntry.() -> Unit,
    private val font: LambdaFont
) : IFontEntry {
    override var text by owner.field("")
    override var position by owner.field(Vec2d.ZERO)

    override var color by owner.field(Color.WHITE)
    override var scale by owner.field(1.0)
    override var shadow by owner.field(true)

    private var shadowSetting by owner.field(true)
    private var shadowBrightness by owner.field(0.0)
    private var shadowShift by owner.field(0.0)
    private var baselineOffset by owner.field(0.0)
    private var gap by owner.field(0.0)
    private val actualScale get() = scale * 0.12

    override fun getWidth(text: String): Double {
        var width = 0.0

        text.forEach { char ->
            val glyph = font[char] ?: return@forEach
            width += glyph.size.x + gap
        }

        return width * actualScale
    }

    override val height: Double
        get() = font.glyphs.fontHeight * actualScale * 0.7

    override fun build(ctx: IRenderContext) = ctx.use {
        val scaledShadowShift = shadowShift * actualScale
        val scaledGap = gap * actualScale
        val shadowColor = getShadowColor(color)

        var posX = 0.0
        val posY = height * -0.5 + baselineOffset * actualScale

        text.toCharArray().forEach { char ->
            val charInfo = font[char] ?: return@forEach
            val scaledSize = charInfo.size * actualScale

            val pos1 = Vec2d(posX, posY)
            val pos2 = pos1 + scaledSize

            if (shadow && FontSettings.shadow) {
                val shadowPos1 = pos1 + scaledShadowShift
                val shadowPos2 = shadowPos1 + scaledSize
                putCharQuad(shadowPos1, shadowPos2, shadowColor, charInfo)
            }

            putCharQuad(pos1, pos2, color, charInfo)

            posX += scaledSize.x + scaledGap
        }
    }

    override fun update() {
        shadowSetting = FontSettings.shadow
        shadowBrightness = FontSettings.shadowBrightness
        shadowShift = FontSettings.shadowShift * 4.0
        baselineOffset = FontSettings.baselineOffset * 2.0f - 20f
        gap = FontSettings.gapSetting * 0.5f - 0.8f

        super.update()
    }

    private fun IRenderContext.putCharQuad(pos1: Vec2d, pos2: Vec2d, color: Color, ci: CharInfo) {
        val x = position.x
        val y = position.y

        grow(4)

        putQuad(
            vec2(pos1.x + x, pos1.y + y).vec2(ci.uv1.x, ci.uv1.y).color(color).end(),
            vec2(pos1.x + x, pos2.y + y).vec2(ci.uv1.x, ci.uv2.y).color(color).end(),
            vec2(pos2.x + x, pos2.y + y).vec2(ci.uv2.x, ci.uv2.y).color(color).end(),
            vec2(pos2.x + x, pos1.y + y).vec2(ci.uv2.x, ci.uv1.y).color(color).end()
        )
    }

    private fun getShadowColor(color: Color) = Color(
        (color.red * shadowBrightness).toInt(),
        (color.green * shadowBrightness).toInt(),
        (color.blue * shadowBrightness).toInt(),
        color.alpha
    )
}

interface IFontEntry : IRenderEntry<IFontEntry> {
    var text: String
    var position: Vec2d

    var color: Color
    var scale: Double
    var shadow: Boolean

    fun getWidth(text: String): Double

    val width get() = getWidth(text)
    val height: Double

    val widthVec get() = Vec2d(width, 0.0)
    val heightVec get() = Vec2d(0.0, height)
}
