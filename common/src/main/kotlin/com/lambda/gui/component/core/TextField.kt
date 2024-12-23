/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui.component.core

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.FontRenderer.drawString
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.VAlign
import com.lambda.gui.component.layout.Layout
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import java.awt.Color

class TextField(
    owner: Layout,
) : Layout(owner, true, true) {
    var text = ""
    var color = Color.WHITE
    var scale = 1.0
    var shadow = true

    val textWidth get() = FontRenderer.getWidth(text, scale)
    val textHeight get() = FontRenderer.getHeight(scale)

    var textHAlignment = HAlign.LEFT
    var textVAlignment = VAlign.CENTER
    var offsetX = 0.0
    var offsetY = 0.0

    private val updateActions = mutableListOf<TextField.() -> Unit>()

    fun onUpdate(block: TextField.() -> Unit) {
        updateActions += block
    }

    init {
        properties.interactionPassthrough = true
        fillParent()

        onRender {
            updateActions.forEach { action ->
                action(this@TextField)
            }

            val rx = renderPositionX + lerp(textHAlignment.multiplier, offsetX, renderWidth - textWidth - offsetX)
            val ry = renderPositionY + lerp(textVAlignment.multiplier, offsetY, renderHeight - textHeight - offsetY)
            val renderPos = Vec2d(rx, ry + textHeight * 0.5)

            drawString(text, renderPos, color, scale, shadow)
        }
    }

    companion object {
        /**
         * Creates a [TextField] component
         */
        @UIBuilder
        fun Layout.textField(
            block: TextField.() -> Unit = {}
        ) = TextField(this).apply(children::add).apply(block)
    }
}
