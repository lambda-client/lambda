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
import com.lambda.gui.component.core.FilledRect.Companion.rectBehind
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.util.Mouse
import com.lambda.util.math.*
import com.lambda.util.math.MathUtils.toInt
import kotlin.math.pow

class ModuleLayout(
    owner: Layout,
    module: Module,
    initialPosition: Vec2d = Vec2d.ZERO,
    initialSize: Vec2d = Vec2d(100, 18)
) : Window(
    owner,
    module.name,
    initialPosition, initialSize,
    false, true, Minimizing.Relative, false,
    AutoResize.ForceEnabled
) {
    private val animation = animationTicker()
    private val cursorController = cursorController()

    private var enableAnimation by animation.exp(0.0, 1.0, 0.6, module::isEnabled)
    private var openAnimation by animation.exp(1.0, 0.0, 0.6, ::isMinimized)

    private val longHovered get() = isHovered || System.currentTimeMillis() - lastHover < 80
    private var hoverAnimation by animation.exp(0.0, 1.0, { if (longHovered) 0.7 else 0.2 }, this::longHovered)
    private val shrink get() = hoverAnimation.pow(3) * lerp(openAnimation, 1.0, 0.5)

    // ToDo: replace with timer
    private var lastHover = 0L

    // Could be true only if owner is ModuleWindow
    var isLast = false

    init {
        isMinimized = true
        height = 100.0
        openAnimation = 0.0

        overrideX { owner.renderPositionX + ClickGui.padding }
        overrideWidth { owner.renderWidth - ClickGui.padding * 2 }

        titleBar.use {
            overrideHeight(ClickGui::moduleHeight)

            onMouseClick { button, action ->
                if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    module.toggle()
                }
            }

            textField.onUpdate {
                textHAlignment = HAlign.LEFT
                offsetX = ClickGui.fontOffset + lerp(
                    openAnimation,
                    hoverAnimation * 2,
                    1.0 + hoverAnimation
                )
            }
        }

        rectBehind(titleBar) {
            onUpdate {
                rectangle = this@ModuleLayout.rect.shrink(shrink)
                shade = ClickGui.backgroundShade

                val openRev = 1.0 - openAnimation     // 1.0  <->  0.0
                val openRevSigned = openRev * 2 - 1   // 1.0  <-> -1.0
                val enableRev = 1.0 - enableAnimation // 1.0  <->  0.0

                var progress = enableAnimation

                // hover: +0.1 to alpha if minimized, -0.1 to alpha if maximized
                progress += hoverAnimation * ClickGui.moduleHoverAccent * openRevSigned

                // +0.4 to alpha if opened and disabled
                progress += openAnimation * ClickGui.moduleOpenAccent * enableRev

                // interpolate and set the color
                setColor(lerp(progress, ClickGui.moduleDisabledColor, ClickGui.moduleEnabledColor))
            }

            onUpdate {
                setRadius(hoverAnimation)

                if (isLast && ClickGui.autoResize) {
                    leftBottomRadius = ClickGui.roundRadius - (ClickGui.padding + shrink)
                    rightBottomRadius = leftBottomRadius
                }
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

        onUpdate {
            if (isHovered) lastHover = System.currentTimeMillis()
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

        module.settings.forEach { setting ->
            content.layoutOf(setting)
        }

        content.overrideContentHeight {
            val settings = content.children
                .filterIsInstance<SettingLayout<*, *>>()

            val components = settings.sumOf {
                (it.renderHeight + ClickGui.listStep) * it.visibilityAnimation
            } - ClickGui.listStep

            components + ClickGui.padding * 2 * settings.isNotEmpty().toInt()
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

        listOf(
            titleBarBackground,
            contentBackground,
            outlineRect
        ).forEach(Layout::destroy)
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
