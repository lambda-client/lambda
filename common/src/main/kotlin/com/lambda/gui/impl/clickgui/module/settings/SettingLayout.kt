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

package com.lambda.gui.impl.clickgui.module.settings

import com.lambda.config.AbstractSetting
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.ModuleLayout.Companion.backgroundTint
import com.lambda.gui.impl.clickgui.core.AnimatedChild
import com.lambda.util.math.*

/**
 * A base class for setting layouts.
 */
abstract class SettingLayout <V : Any, T: AbstractSetting<V>> (
    owner: Layout,
    val setting: T,
    expandable: Boolean = false
) : AnimatedChild(
    owner,
    setting.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, false,
    if (expandable) Minimizing.Absolute else Minimizing.Disabled,
    false,
    AutoResize.ForceEnabled
) {
    protected val cursorController = cursorController()

    var settingDelegate by setting
    val isVisible get() = setting.visibility()

    override val isShown: Boolean get() = super.isShown && isVisible

    init {
        isMinimized = true

        onUpdate {
            width = owner.width
        }

        titleBar.onUpdate {
            height = ClickGui.settingsHeight
        }

        if (!expandable) {
            onUpdate {
                height = titleBar.height
            }
            content.destroy()
        } else {
            backgroundTint(true)
        }

        titleBar.textField.onUpdate {
            scale *= 0.92
        }
    }
}
