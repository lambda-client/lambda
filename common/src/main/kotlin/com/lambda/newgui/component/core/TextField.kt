package com.lambda.newgui.component.core

import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import java.awt.Color

class TextField(
    owner: Layout,
    var text: String,
    var color: Color,
    var scale: Double,
    var bold: Boolean,
    var shadow: Boolean,
    var offset: Double,
) : Layout(owner, true, true) {
    val textWidth  get() = fr.getWidth(text, scale)
    val textHeight get() = fr.getHeight(scale)

    private val fr get() = if (bold) renderer.boldFont else renderer.font

    init {
        properties.interactionPassthrough = true
        verticalAlignment = VAlign.CENTER

        onRender {
            position = owner.position
            size = owner.size

            val x = lerp(
                horizontalAlignment.multiplier,
                rect.left,
                rect.right - textWidth,
            ) - offset * horizontalAlignment.offset

            val y = when {
                verticalAlignment == VAlign.CENTER || rect.size.y <= textHeight -> rect.center.y
                else -> lerp(
                    verticalAlignment.multiplier,
                    rect.top + textHeight * 0.5,
                    rect.bottom - textHeight * 0.5
                )
            }

            fr.build(text, Vec2d(x, y), color, scale, shadow)
        }
    }

    companion object {
        /**
         * Creates a [TextField] component
         *
         * @param text String to draw
         *
         * @param color Color of the font
         *
         * @param scale Scale of the font
         *
         * @param bold Whether to use the bold variant of the font
         *
         * @param shadow Whether the font should drop a shadow
         *
         * @param offset Offset from the corner(specified by [horizontalAlignment]) of the text (ignored for [HAlign.CENTER])
         */
        @UIBuilder
        fun Layout.textField(
            text: String,
            color: Color = Color.WHITE,
            scale: Double = 1.0,
            bold: Boolean = false,
            shadow: Boolean = true,
            offset: Double = 0.0,
            block: TextField.() -> Unit = {}
        ) = TextField(this, text, color, scale, bold, shadow, offset).apply(children::add).apply(block)
    }
}