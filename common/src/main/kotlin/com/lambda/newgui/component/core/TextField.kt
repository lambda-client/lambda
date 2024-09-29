package com.lambda.newgui.component.core

import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import java.awt.Color

class TextField(
    owner: Layout,
    initialText: String,
    initialColor: Color = Color.WHITE,
    initialScale: Double = 1.0,
    initialShadow: Boolean = true,
    initialOffset: Double = 0.0,
) : Layout(owner, true, true) {
    var text = initialText
    var color = initialColor
    var scale = initialScale
    var shadow = initialShadow

    var offset = initialOffset

    init {
        properties.interactionPassthrough = true
        verticalAlignment = VAlign.CENTER
        rectUpdate(owner::rect)

        onRender {
            val w = font.getWidth(text, scale)
            val h = font.getHeight(scale)

            val x = lerp(
                horizontalAlignment.multiplier,
                rect.left,
                rect.right - w,
            ) - offset * horizontalAlignment.offset

            val y = when {
                verticalAlignment == VAlign.CENTER || rect.size.y <= h -> rect.center.y
                else -> lerp(
                    verticalAlignment.multiplier,
                    rect.top + h * 0.5,
                    rect.bottom - h * 0.5
                )
            }

            font.build(text, Vec2d(x, y), color, scale, shadow)
        }
    }

    companion object {
        @UIBuilder
        fun Layout.textField(
            text: String,
            color: Color = Color.WHITE,
            scale: Double = 1.0,
            shadow: Boolean = true,
            offset: Double = 0.0,
            block: TextField.() -> Unit = {}
        ) = TextField(this, text, color, scale, shadow, offset).apply(children::add).apply(block)
    }
}