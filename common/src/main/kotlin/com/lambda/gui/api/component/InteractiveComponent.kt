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

package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

abstract class InteractiveComponent : IComponent {
    protected open val hovered get() = rect.contains(lastMouse)
    protected var activeButton: Mouse.Button? = null

    protected open fun onPress(e: GuiEvent.MouseClick) {}
    protected open fun onRelease(e: GuiEvent.MouseClick) {}

    private var lastMouse = Vec2d.ZERO

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Show -> {
                lastMouse = Vec2d.ONE * -1000.0
                activeButton = null
            }

            is GuiEvent.MouseMove -> {
                lastMouse = e.mouse
            }

            is GuiEvent.MouseClick -> {
                lastMouse = e.mouse

                val prevPressed = activeButton != null
                activeButton =
                    if (hovered && e.button.isMainButton && e.action == Mouse.Action.Click) e.button else null
                val pressed = activeButton != null

                if (prevPressed == pressed) return
                if (pressed) onPress(e)
                else onRelease(e)
            }
        }
    }
}
