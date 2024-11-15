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

package com.lambda.gui.impl.clickgui.buttons

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.util.math.lerp

abstract class SettingButton<V : Any, T : AbstractSetting<V>>(
    val setting: T,
    final override val owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : ListButton(owner) {
    override val text = setting.name
    protected var value by setting

    val visible; get() = setting.visibility()
    private var prevTickVisible = false

    private var visibilityAnimation by animation.exp(0.0, 1.0, 0.6, ::visible)
    override val showAnimation get() = lerp(visibilityAnimation, 0.0, super.showAnimation)
    override val renderHeightOffset get() = renderHeightAnimation + lerp(visibilityAnimation, -size.y, 0.0)
    override var activeAnimation = 0.0

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e !is GuiEvent.Tick) return

        if (!prevTickVisible && visible) renderHeightAnimation = heightOffset
        prevTickVisible = visible

        if (!visible) unfocus()
    }

    open fun unfocus() {}
}
