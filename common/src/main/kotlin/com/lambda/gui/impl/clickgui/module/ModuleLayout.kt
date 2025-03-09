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
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.GuiManager.layoutOf
import com.lambda.gui.component.core.FilledRect
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.FilledRect.Companion.rectBehind
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.impl.clickgui.ModuleWindow
import com.lambda.gui.impl.clickgui.core.AnimatedChild
import com.lambda.util.Mouse
import com.lambda.util.math.*
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

    val backgroundRect = rectBehind(titleBar) { // base rect with lowest y to avoid children overlying
        onUpdate {
            rect = this@ModuleLayout.rect.shrink(shrink)
            shade = ClickGui.backgroundShade

            val openRev = 1.0 - openAnimation     // 1.0  <->  0.0
            val openRevSigned = openRev * 2 - 1   // 1.0  <-> -1.0
            val enableRev = 1.0 - enableAnimation // 1.0  <->  0.0

            var progress = enableAnimation

            // hover: +0.1 to alpha if minimized, -0.1 to alpha if maximized and enabled
            progress += hoverAnimation * ClickGui.moduleHoverAccent *
                    lerp(enableAnimation, 1.0, openRevSigned)

            // +0.4 to alpha if opened and disabled
            progress += openAnimation * ClickGui.moduleOpenAccent * enableRev

            // interpolate and set the color
            setColor(
                lerp(progress,
                    ClickGui.moduleDisabledColor,
                    ClickGui.moduleEnabledColor
                ).multAlpha(showAnimation)
            )

            setRadius(hoverAnimation)

            if (isLast && ClickGui.autoResize) {
                leftBottomRadius = ClickGui.roundRadius - (ClickGui.padding + shrink)
                rightBottomRadius = leftBottomRadius
            }
        }

        rect { // hover fx
            onUpdate {
                val base = this@rect.owner as FilledRect

                position = base.position
                size = base.size
                shade = base.shade

                setRadius(
                    base.leftTopRadius,
                    base.rightTopRadius,
                    base.rightBottomRadius,
                    base.leftBottomRadius
                )

                val hoverColor = Color.WHITE.setAlpha(
                    ClickGui.moduleHoverAccent * hoverAnimation * (1.0 - openAnimation) * showAnimation
                )

                setColorH(hoverColor.setAlpha(0.0), hoverColor)
            }
        }
    }

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

        onWindowExpand {
            if (ClickGui.multipleSettingWindows) return@onWindowExpand

            val base = owner // window content
                .owner // window
                ?.owner  // environment with windows
                ?: return@onWindowExpand

            base.children.filterIsInstance<ModuleWindow>().forEach { window ->
                window.content.children.filterIsInstance<ModuleLayout>().forEach { module ->
                    if (module != this) module.isMinimized = true
                }
            }
        }

        val settings = module.settings.map { setting ->
            content.layoutOf(setting)
        }.filterIsInstance<SettingLayout<*, *>>()

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
            check(this is SettingLayout<*, *> || this is ModuleLayout || this is ModuleWindow)

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
