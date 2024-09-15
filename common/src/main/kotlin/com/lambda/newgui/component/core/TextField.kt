package com.lambda.newgui.component.core

import com.lambda.newgui.Layout
import com.lambda.newgui.UIBuilder
import com.lambda.newgui.component.HAlign
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

class TextField(
    owner: Layout,
    initialText: String,
    initialColor: Color = Color.WHITE,
    initialScale: Double = 1.0,
    initialShadow: Boolean = true,
    initialAlignment: HAlign = HAlign.LEFT,
    initialOffset: Double = 0.0,
) : Layout(owner, true, true) {
    var text = initialText
    var color = initialColor
    var scale = initialScale
    var shadow = initialShadow

    var alignment = initialAlignment
    var offset = initialOffset

    // Let user interact through the text
    override val passInteractions = true

    init {
        rect {
            // Completely fill parent component by default
            Rect(Vec2d.ZERO, owner.rect.size)
        }

        onRender {
            val x = lerp(
                rect.left,
                rect.right - font.getWidth(text, scale),
                alignment.multiplier
            ) - offset * alignment.offset

            font.build(text, Vec2d(x, rect.center.y), color, scale, shadow)
        }
    }

    companion object {
        @UIBuilder
        fun Layout.textField(
            text: String,
            color: Color = Color.WHITE,
            scale: Double = 1.0,
            shadow: Boolean = true,
            alignment: HAlign = HAlign.LEFT,
            offset: Double = 0.0,
            block: TextField.() -> Unit = {}
        ) = TextField(this, text, color, scale, shadow, alignment, offset).apply(children::add).apply(block)
    }
}