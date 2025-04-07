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

package com.lambda.gui.impl.clickgui.module.setting

import com.lambda.gui.component.core.LayoutBuilder
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.ModuleLayout.Companion.backgroundTint
import com.lambda.gui.impl.clickgui.core.AnimatedChild
import com.lambda.util.math.*
import kotlin.reflect.KMutableProperty0

/**
 * A base class for setting layouts.
 */
abstract class SettingLayout <V : Any> (
    owner: Layout,
    name: String,
    private val property: KMutableProperty0<V>,
    expandable: Boolean = false
) : AnimatedChild(
    owner,
    name,
    Vec2d.ZERO, Vec2d(110.0, 0.0),
    false, false,
    if (expandable) Minimizing.Absolute else Minimizing.Disabled,
    false,
    AutoResize.ForceEnabled
) {
    protected val cursorController = cursorController()

    protected var settingDelegate: V
        get() = property.get()
        set(value) {
            if (property.get() == value) return
            property.set(value)
            onValueSet.forEach { it(value) }
        }

    private var onValueSet = mutableListOf<(V) -> Unit>()
    private var getVisibilityBlock = { true }
    val isVisible get() = getVisibilityBlock()

    @LayoutBuilder
    fun visibility(action: () -> Boolean) {
        getVisibilityBlock = { action() }
    }

    @LayoutBuilder
    fun onValueSet(action: (V) -> Unit) {
        onValueSet += action
    }

    override val isShown: Boolean get() = super.isShown && isVisible

    init {
        isMinimized = true

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
