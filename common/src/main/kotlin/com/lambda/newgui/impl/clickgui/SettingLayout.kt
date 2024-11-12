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

import com.lambda.config.AbstractSetting
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.window.Window
import com.lambda.util.math.Vec2d

/**
 * A base class for setting layouts.
 */
abstract class SettingLayout <V : Any, T: AbstractSetting<V>> (
    owner: Layout,
    val setting: T,
    expandable: Boolean = false
) : Window( // going to use window to easily implement expandable settings (such as color picker)
    owner,
    setting.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, false,
    if (expandable) Minimizing.Relative else Minimizing.Disabled,
    false,
    AutoResize.ForceEnabled,
    true
) {
    protected val animation = animationTicker()
    protected val cursorController = cursorController()

    var settingValue by setting

    init {
        overrideWidth(owner::renderWidth)
        titleBar.overrideHeight(NewCGui::settingsHeight)
        minimized = true

        with(titleBar.textField) {
            text = setting.name
            bold = false
            textHAlignment = HAlign.LEFT

            onUpdate {
                scale = NewCGui.fontScale * 0.92
            }
        }

        children.removeAll(listOf(titleBarRect, contentRect, outlineRect))
        if (!expandable) children.remove(content)
    }
}
