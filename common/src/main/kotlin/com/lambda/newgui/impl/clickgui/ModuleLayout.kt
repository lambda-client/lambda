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

package com.lambda.newgui.impl.clickgui

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.Module
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.GuiManager.layoutOf
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.core.FilledRect
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.window.Window
import com.lambda.newgui.impl.clickgui.settings.BooleanButton.Companion.booleanSetting
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp

class ModuleLayout(
    owner: Layout,
    module: Module
) : Window(
    owner,
    module.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, true, Minimizing.Relative, false,
    AutoResize.ForceEnabled,
    true
) {
    private val animation = animationTicker()
    private val cursorController = cursorController()

    private var enableAnimation by animation.exp(0.0, 1.0, 0.6, module::isEnabled)

    // Could be true only if owner is ModuleWindow
    var isLast = false

    init {
        minimized = true
        height = 100.0

        overrideX { owner.renderPositionX + NewCGui.padding }
        overrideWidth { owner.renderWidth - NewCGui.padding * 2 }

        with(titleBar) {
            with(textField) {
                bold = false
                textHAlignment = HAlign.LEFT

                onUpdate {
                    offsetX = NewCGui.fontOffset
                }
            }

            onMouseClick { button, action ->
                if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    module.toggle()
                }
            }
        }

        onShow {
            enableAnimation = 0.0
        }

        onHide {
            cursorController.reset()
        }

        titleBarRect.onUpdate {
            setColor(lerp(enableAnimation, NewCGui.moduleDisabledColor, NewCGui.moduleEnabledColor))
            correctRadius()
        }

        contentRect.onUpdate {
            setColor(lerp(enableAnimation, NewCGui.moduleDisabledColor, NewCGui.moduleEnabledColor))
            correctRadius()
        }

        children.remove(outlineRect)

        onTick {
            val cursor = if (titleBar.isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
            cursorController.setCursor(cursor)
        }

        content.apply {
            module.settings.forEach { setting -> layoutOf(setting) }
        }
    }

    private fun FilledRect.correctRadius() {
        if (!isLast) {
            setRadius(0.0)
            return
        }

        leftTopRadius = 0.0
        rightTopRadius = 0.0
        leftBottomRadius -= NewCGui.padding
        rightBottomRadius -= NewCGui.padding
    }

    companion object {
        /**
         * Creates a [ModuleLayout] - visual representation of the [Module]
         */
        @UIBuilder
        fun Layout.moduleLayout(module: Module) =
            ModuleLayout(this, module).apply(children::add)
    }
}
