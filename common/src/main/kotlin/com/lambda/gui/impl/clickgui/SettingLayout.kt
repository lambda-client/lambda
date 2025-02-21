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

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import com.lambda.util.math.transform
import java.awt.Color

/**
 * A base class for setting layouts.
 */
abstract class SettingLayout <V : Any, T: AbstractSetting<V>> (
    owner: Layout,
    val setting: T,
    expandable: Boolean = false
) : Window(
    owner,
    setting.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, false,
    if (expandable) Minimizing.Relative else Minimizing.Disabled,
    false,
    AutoResize.ForceEnabled
) {
    protected val animation = animationTicker()
    protected val cursorController = cursorController()

    var visibilityAnimation by animation.exp(0.0, 1.0, 0.8, ::visible)
    var heightOffset = 0.0

    var settingValue by setting
    val visible get() = setting.visibility()

    override val renderChildren: Boolean
        get() = visibilityAnimation > 0

    init {
        isMinimized = true

        overrideWidth(owner::renderWidth)
        titleBar.overrideHeight(ClickGui::settingsHeight)

        overrideX {
            owner.renderPositionX + transform(visibilityAnimation, 0.0, 1.0, -10.0, 0.0)
        }

        titleBar.textField.use {
            text = setting.name
            textHAlignment = HAlign.LEFT

            onUpdate {
                scale = ClickGui.fontScale * 0.92 * lerp(visibilityAnimation, 0.6, 1.0)
                color = Color.WHITE.setAlpha(visibilityAnimation)
            }
        }

        listOf(
            titleBarBackground,
            contentBackground,
            outlineRect
        ).forEach(Layout::destroy)

        if (!expandable) content.destroy()
    }
}
