/*
 * Copyright 2025 Lambda
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

package com.lambda.gui.impl.clickgui.module

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.GuiManager.layoutOf
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.FilledRect.Companion.rectBehind
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.impl.clickgui.ModuleWindow
import com.lambda.gui.impl.clickgui.core.AnimatedChild
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.setAlpha
import java.awt.Color

class ModuleLayout(
    owner: Layout,
    module: Module,
    initialPosition: Vec2d = Vec2d.ZERO,
    initialSize: Vec2d = Vec2d(100, 18)
) : AnimatedChild(
    owner,
    module.name,
    initialPosition, initialSize,
    false, false, Minimizing.Absolute, false,
    AutoResize.ForceEnabled
) {
    private val cursorController = cursorController()

    private var enableAnimation by animation.exp(0.0, 1.0, 0.6, module::isEnabled)

    val backgroundRect = animatedBackground(::enableAnimation)

    init {
        backgroundTint()

        onUpdate {
            positionX = owner.positionX + ClickGui.padding
            width = owner.width - ClickGui.padding * 2
        }

        titleBar.use {
            onUpdate {
                height = ClickGui.moduleHeight
            }

            onMouseAction(Mouse.Button.Left) {
                module.toggle()
            }
        }

        onShow {
            enableAnimation = 0.0
            hoverAnimation = 0.0
            isMinimized = true
        }

        onTick {
            val cursor = if (titleBar.isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
            cursorController.setCursor(cursor)
        }

        val settings = module.settings.mapNotNull { setting ->
            content.layoutOf(setting)
        }.map { it as SettingLayout<*> }.onEach {
            it.onUpdate {
                width = this@ModuleLayout.content.width
            }
        }

        val minimizeSettings = {
            settings.forEach {
                it.isMinimized = true
            }
        }

        onWindowExpand { minimizeSettings() }
        onWindowMinimize { minimizeSettings() }
        content.listify()
    }

    companion object {
        /**
         * Creates a [ModuleLayout] - visual representation of the [Module]
         */
        @UIBuilder
        fun Layout.moduleLayout(module: Module) =
            ModuleLayout(this, module).apply(children::add)

        /**
         * Used to dark the background of the settings a bit
         *
         * Not for external usage
         */
        @UIBuilder
        fun Window.backgroundTint(tintTitleBar: Boolean = false) {
            check(this is SettingLayout<*> || this is ModuleLayout || this is ModuleWindow)

            val base = this@backgroundTint

            rectBehind(content) {
                onUpdate {
                    rect = if (tintTitleBar) base.rect
                    else Rect(titleBar.leftBottom, base.rightBottom)

                    setColor(Color.BLACK.setAlpha(0.08 * heightAnimation))

                    val round = (base as? ModuleLayout?)?.backgroundRect
                        ?: (base as? ModuleWindow)?.contentBackground

                    round?.let {
                        leftBottomRadius = it.leftBottomRadius
                        rightBottomRadius = it.rightBottomRadius
                    }
                }

                val bg = this

                rect { // top shadow
                    onUpdate {
                        position = bg.position
                        width = bg.width
                        height = titleBar.height * 0.2

                        setColorV(Color.BLACK.setAlpha(0.1 * heightAnimation), Color.BLACK.setAlpha(0.0))
                    }
                }
            }
        }
    }
}
