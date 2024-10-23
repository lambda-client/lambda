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

package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.KeyCode
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import com.lambda.util.math.setAlpha
import java.awt.Color
import kotlin.math.abs

abstract class InputBarOverlay(
    val renderer: RenderLayer,
    owner: ChildLayer.Drawable<InputBarOverlay, *>
) : ChildComponent(owner) {
    override val rect: Rect get() = owner.rect
    override var isActive = false

    protected abstract val pressAnimation: Double
    protected abstract val interactAnimation: Double
    protected abstract val hoverFontAnimation: Double
    protected abstract val showAnimation: Double
    protected open val isKeyBind: Boolean = false

    val activeAnimation by owner.gui.animation.exp(0.0, 1.0, 0.7, ::isActive)
    private var typeAnimation by owner.gui.animation.exp({ 0.0 }, 0.2)

    private var targetOffset = 0.0
    private var offset by owner.gui.animation.exp(::targetOffset, 0.4)

    abstract fun getText(): String
    open fun setStringValue(string: String) {}
    open fun setKeyValue(key: KeyCode) {}

    open fun isCharAllowed(string: String, char: Char): Boolean = true

    private var typed = ""

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                isActive = false
            }

            is GuiEvent.Render -> {
                // Value text
                renderer.font.apply {
                    val text = getText()
                    val scale = lerp(1.0 - activeAnimation, 0.5, 1.0)
                    val position =
                        Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + getWidth(text, scale), 0.0)
                    val color = Color.WHITE.setAlpha(lerp(showAnimation, 0.0, 1.0 - activeAnimation))

                    build(text, position, color, scale)
                }

                val textStartX = rect.left + ClickGui.windowPadding + interactAnimation + hoverFontAnimation
                val textColor = Color.WHITE.setAlpha(lerp(showAnimation, 0.0, activeAnimation))

                // Typing field
                renderer.font.apply {
                    val scale = lerp(activeAnimation, 0.5, 1.0) - pressAnimation * 0.08
                    val position = Vec2d(textStartX, rect.center.y)

                    targetOffset = getWidth(typed, scale)
                    build(typed, position, textColor, scale)
                }

                // Separator
                renderer.filled.apply {
                    val shrink = lerp(activeAnimation, rect.size.y * 0.5, 2 + abs(typeAnimation))

                    val rect = Rect(
                        Vec2d(0.0, rect.top + shrink),
                        Vec2d(1.0, rect.bottom - shrink)
                    ) + Vec2d(lerp(activeAnimation, rect.right, textStartX + offset + 2), 0.0)

                    build(rect, color = textColor.multAlpha(0.8))
                }
            }

            is GuiEvent.CharTyped -> {
                if (!isActive || !isCharAllowed(typed, e.char) || isKeyBind) return
                typed += e.char
                typeAnimation = 1.0
            }

            is GuiEvent.KeyPress -> {
                if (!isActive) return

                if (isKeyBind) {
                    val key = when (e.key) {
                        KeyCode.DELETE, KeyCode.BACKSPACE -> KeyCode.UNBOUND
                        KeyCode.ESCAPE -> return
                        else -> e.key
                    }

                    setKeyValue(key)
                    toggle()
                    return
                }

                when (e.key) {
                    KeyCode.ENTER -> {
                        setStringValue(typed)
                        toggle()
                    }

                    KeyCode.BACKSPACE -> {
                        typed = typed.dropLast(1)
                        typeAnimation = -1.0
                    }

                    else -> {}
                }
            }
        }
    }

    fun toggle() {
        isActive = !isActive
        if (isActive) typed = getText().filter { isCharAllowed("", it) }
    }
}
