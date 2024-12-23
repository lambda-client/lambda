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

package com.lambda.gui.impl.clickgui

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.GuiManager.layoutOf
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.core.FilledRect
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.util.Mouse
import com.lambda.util.math.*
import java.awt.Color

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
    private var openAnimation by animation.exp(1.0, 0.0, 0.6, ::minimized)

    // Could be true only if owner is ModuleWindow
    var isLast = false

    init {
        minimized = true
        height = 100.0

        overrideX { owner.renderPositionX + ClickGui.padding }
        overrideWidth { owner.renderWidth - ClickGui.padding * 2 }

        with(titleBar) {
            with(textField) {
                textHAlignment = HAlign.LEFT

                onUpdate {
                    offsetX = ClickGui.fontOffset
                }
            }

            overrideHeight(ClickGui::moduleHeight)

            onMouseClick { button, action ->
                if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    module.toggle()
                }
            }
        }

        content.overrideContentHeight {
            val settings = content.children
                .filterIsInstance<SettingLayout<*, *>>()

            val components = settings.sumOf {
                (it.renderHeight + ClickGui.listStep) * it.visibilityAnimation
            } - ClickGui.listStep

            val padding = ClickGui.padding * 2
            components + if (settings.isNotEmpty()) padding else 0.0
        }

        content.reorderChildren {
            val settings = content.children
                .filterIsInstance<SettingLayout<*, *>>()

            var y = 0.0

            settings.forEach {
                if (it.visible) {
                    it.heightOffset = y
                }

                y += (it.renderHeight + ClickGui.listStep) * it.visibilityAnimation

                it.overrideY {
                    content.renderPositionY + content.renderScrollOffset + ClickGui.padding + it.heightOffset
                }
            }
        }

        rect { // Separator
            onUpdate {
                val vec = Vec2d(
                    lerp(openAnimation, titleBar.renderWidth * 0.5, ClickGui.fontOffset * 0.5),
                    -0.25
                )

                rectangle = Rect(
                    pos1 = titleBar.leftBottom + vec,
                    pos2 = titleBar.rightBottom - vec
                )

                setColor(lerp(enableAnimation, Color.WHITE, Color.BLACK).setAlpha(0.2 * openAnimation))
                shade = ClickGui.outlineShade
            }
        }

        titleBarRect.onUpdate {
            setColor(lerp(enableAnimation, ClickGui.moduleDisabledColor, ClickGui.moduleEnabledColor))
            correctRadius()
        }

        contentRect.onUpdate {
            setColor(lerp(enableAnimation, ClickGui.moduleDisabledColor, ClickGui.moduleEnabledColor))
            correctRadius()
        }

        children.remove(outlineRect)

        onShow {
            enableAnimation = 0.0
        }

        onTick {
            val cursor = if (titleBar.isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
            cursorController.setCursor(cursor)
        }

        module.settings.forEach { setting ->
            content.layoutOf(setting)
        }
    }

    private fun FilledRect.correctRadius() {
        if (!isLast || !ClickGui.autoResize) {
            setRadius(0.0)
            return
        }

        leftTopRadius = 0.0
        rightTopRadius = 0.0
        leftBottomRadius -= ClickGui.padding
        rightBottomRadius -= ClickGui.padding
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
