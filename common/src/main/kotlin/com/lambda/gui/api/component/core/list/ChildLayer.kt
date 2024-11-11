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

package com.lambda.gui.api.component.core.list

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.Mouse
import com.lambda.util.math.Rect

open class ChildLayer<T : ChildComponent, R : IComponent>(
    val gui: LambdaGui,
    val ownerComponent: R,
    private val childRect: () -> Rect,
    private val childAccessible: (T) -> Boolean = { true },
) : IComponent {
    override val isActive get() = ownerComponent.isActive
    override val childShowAnimation get() = ownerComponent.childShowAnimation
    override val rect get() = childRect()

    val children = mutableListOf<T>()

    override fun onEvent(e: GuiEvent) {
        children.forEach { child ->
            when (e) {
                is GuiEvent.Tick -> {
                    val ownerAccessible = (ownerComponent as? ChildComponent)?.accessible ?: true
                    child.accessible =
                        childAccessible(child) && child.rect in rect && ownerAccessible && ownerComponent.isActive
                }

                is GuiEvent.KeyPress, is GuiEvent.CharTyped, is GuiEvent.MouseScroll -> {
                    if (!child.accessible) return@forEach
                }

                is GuiEvent.MouseClick -> {
                    val newAction = if (child.accessible) e.action else Mouse.Action.Release
                    val newEvent = GuiEvent.MouseClick(e.button, newAction, e.mouse)
                    child.onEvent(newEvent)
                    return@forEach
                }
            }

            child.onEvent(e)
        }
    }

    class Drawable<T : ChildComponent, R : IComponent>(
        gui: LambdaGui,
        owner: R,
        val renderer: RenderLayer,
        contentRect: () -> Rect,
        childAccessible: (T) -> Boolean = { true },
    ) : ChildLayer<T, R>(gui, owner, contentRect, childAccessible)
}
