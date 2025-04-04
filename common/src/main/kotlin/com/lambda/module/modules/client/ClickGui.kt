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

package com.lambda.module.modules.client

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.gui.LambdaScreen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.gui.RootLayout.Companion.gui
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.VAlign
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.GlowRect.Companion.glow
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.component.window.Window.Companion.window
import com.lambda.gui.impl.clickgui.ModuleWindow.Companion.moduleWindow
import com.lambda.gui.impl.clickgui.core.AnimatedChild.Companion.animatedBackground
import com.lambda.gui.impl.clickgui.module.setting.settings.UnitButton.Companion.unitButton
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d
import com.lambda.util.math.setAlpha
import java.awt.Color
import kotlin.math.hypot

object ClickGui : Module(
    name = "ClickGui",
    description = "sexy again",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val titleBarHeight by setting("Title Bar Height", 18.0, 10.0..25.0, 0.1)
    val moduleHeight by setting("Module Height", 16.0, 10.0..25.0, 0.1)
    val settingsHeight by setting("Settings Height", 16.0, 10.0..25.0, 0.1)
    val padding by setting("Padding", 1.0, 1.0..6.0, 0.1)
    val listStep by setting("List Step", 1.0, 0.0..6.0, 0.1)
    val autoResize by setting("Auto Resize", true)

    val roundRadius by setting("Round Radius", 3.0, 0.0..10.0, 0.1)

    val blurStrength by setting("Blur Strength", 2, 0..10, 1)
    val hudBlur by setting("HUD Blur", true) { blurStrength > 0 }

    val backgroundTint by setting("Background Tint", Color.BLACK.setAlpha(0.4))
    val backgroundCornerTint by setting("Background Corner Tint", Color.WHITE.setAlpha(0.4))
    val cornerTintWidth by setting("Corner Tint Width", 1.0, 1.0..500.0, 0.1)
    val cornerTintShade by setting("Corner Tint Shade", true)

    val titleBackgroundColor by setting("Title Background Color", Color(80, 80, 80, 180))
    val backgroundColor by setting("Background Color", titleBackgroundColor)
    val backgroundShade by setting("Background Shade", true)

    val outline by setting("Outline", true)
    val outlineWidth by setting("Outline Width", 1.0, 0.5..5.0, 0.1) { outline }
    val outlineColor by setting("Outline Color", Color.WHITE) { outline }
    val outlineShade by setting("Outline Shade", true) { outline }

    val glow by setting("Glow", true)
    val glowWidth by setting("Glow Width", 8.0, 1.0..20.0, 0.1) { glow }
    val glowColor by setting("Glow Color", Color.WHITE.setAlpha(0.35)) { glow }
    val glowShade by setting("Glow Shade", true) { glow }

    val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1)
    val fontOffset by setting("Font Offset", 4.0, 0.0..5.0, 0.1)

    val moduleEnabledColor by setting("Module Enabled Color", Color.WHITE.setAlpha(0.6))
    val moduleDisabledColor by setting("Module Disabled Color", Color.WHITE.setAlpha(0.0))
    val moduleHoverAccent by setting("Module Hover Accent", 0.15, 0.0..0.3, 0.01)
    val moduleOpenAccent by setting("Module Open Accent", 0.3, 0.0..0.5, 0.01)

    val multipleSettingWindows by setting("Multiple Setting Windows", false)
    val animationCurve by setting("List Animation Curve", AnimationCurve.Reverse)
    val smoothness by setting("Smoothness", 0.4, 0.3..0.7, 0.01) { animationCurve != AnimationCurve.Static }

    val hudPadding by setting("Hud Padding", 3.0, 0.0..10.0, 0.1)

    val SCREEN: LambdaScreen by lazy {
        gui("Click Gui") {
            onKeyPress {
                if (it.keyCode != keybind.keyCode || keybind == KeyCode.UNBOUND) return@onKeyPress
                mc.currentScreen?.close()
            }

            rect {
                onUpdate {
                    rect = owner!!.rect
                    setColor(backgroundTint)
                }
            }

            glow {
                onUpdate {
                    rect = owner!!.rect
                    innerSpread = cornerTintWidth
                    shade = cornerTintShade

                    setColor(backgroundCornerTint)
                    setInnerRadius(cornerTintWidth)
                }
            }

            var x = 10.0
            val y = x

            window( // wrap module windows within a scrollable window
                draggable = false,
                minimizing = Window.Minimizing.Disabled
            ) { window ->
                window.use {
                    titleBar.onUpdate {
                        height = 0.0
                    }

                    onUpdate {
                        position = Vec2d.ZERO
                        size = RenderMain.screenSize

                        content.freeScroll = true
                        content.scrollable = ClickGui.autoResize
                    }

                    listOf(
                        titleBar.textField,
                        titleBarBackground,
                        contentBackground,
                        outlineRect,
                        glowRect
                    ).forEach(Layout::destroy)
                }

                ModuleTag.defaults.forEach { tag ->
                    x += moduleWindow(tag, Vec2d(x, y)).width + 5
                }
            }

            switchButton("HUD", ::HUD)
        }
    }

    val HUD: LambdaScreen by lazy {
        gui("Hud GUI") {
            switchButton("Back", ::SCREEN)

            var dragInfo: Pair<HudModule, Vec2d>? = null

            onShow {
                dragInfo = null
            }

            onMouse(action = Mouse.Action.Click, button = Mouse.Button.Left) {
                dragInfo = null
            }

            onMouseMove { mouse ->
                if (pressedButton != Mouse.Button.Left) return@onMouseMove

                ModuleRegistry.modules
                    .filterIsInstance<HudModule>()
                    .filter { mouse in it.getRootLayout().rect }
                    .minByOrNull {
                        val (x, y) = it.getRootLayout().rect.center
                        hypot(x - mouse.x, y - mouse.y)
                    }?.let { module ->
                        dragInfo = dragInfo ?: (module to (mouse - module.getRootLayout().position))

                    }

                dragInfo?.let { drag ->
                    drag.first.getRootLayout().position = mouse - drag.second
                }
            }
        }
    }

    enum class AnimationCurve {
        Normal,
        Static,
        Reverse
    }

    init {
        onEnable {
            SCREEN.show()
            toggle()
        }
    }

    private fun Layout.switchButton(text: String, gui: () -> LambdaScreen) {
        unitButton(text) {
            gui().show()
        }.apply {
            animatedBackground {
                hoverAnimation * 0.5 + 0.5
            }

            horizontalAlignment = HAlign.RIGHT
            verticalAlignment = VAlign.BOTTOM
            titleBar.textField.textHAlignment = HAlign.CENTER

            positionX = owner!!.positionX - width - 10.0
            positionY = owner.positionY - height - 10.0

            onUpdate {
                width = height * 1.5
            }
        }
    }
}
