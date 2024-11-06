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

package com.lambda.newgui.component.window

import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.TextField.Companion.textField
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

/**
 * Represents a titlebar component
 */
class TitleBar(
    owner: Window,
    title: String,
    drag: Boolean
) : Layout(owner, true, true) {
    val textField = textField {
        text = title
        bold = true

        textHAlignment = HAlign.CENTER

        onUpdate {
            offsetX = NewCGui.fontOffset
            scale = NewCGui.fontScale
        }
    }

    private var dragOffset: Vec2d? = null

    init {
        overrideSize(
            owner::renderWidth,
            NewCGui::titleBarHeight
        )

        onShow {
            dragOffset = null
        }

        onMouseClick { button: Mouse.Button, action: Mouse.Action ->
            dragOffset = if (drag && button == Mouse.Button.Left && action == Mouse.Action.Click) {
                mousePosition - owner.position
            } else null
        }

        onMouseMove { mouse ->
            dragOffset?.let { drag ->
                owner.position = mouse - drag
            }
        }
    }

    companion object {
        @UIBuilder
        fun Window.titleBar(
            text: String,
            drag: Boolean
        ) = TitleBar(this, text, drag).apply(children::add)
    }
}
