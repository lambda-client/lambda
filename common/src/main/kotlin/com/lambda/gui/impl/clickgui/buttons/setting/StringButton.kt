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

package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.StringSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.InputBarOverlay
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.math.multAlpha

class StringButton(
    setting: StringSetting,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : SettingButton<String, StringSetting>(setting, owner) {
    private val layer = ChildLayer.Drawable(owner.gui, this, owner.renderer, ::rect, InputBarOverlay::isActive)
    private val inputBar: InputBarOverlay = object : InputBarOverlay(renderer, layer) {
        override val pressAnimation get() = this@StringButton.pressAnimation
        override val interactAnimation get() = this@StringButton.interactAnimation
        override val hoverFontAnimation get() = this@StringButton.hoverFontAnimation
        override val showAnimation get() = this@StringButton.showAnimation

        override fun getText() = value
        override fun setStringValue(string: String) {
            value = string
        }
    }.apply(layer.children::add)

    override val textColor get() = super.textColor.multAlpha(1.0 - inputBar.activeAnimation)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)
        layer.onEvent(e)
    }

    override fun unfocus() {
        inputBar.isActive = false
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (!inputBar.isActive) (owner.gui as? AbstractClickGui)?.unfocusSettings()
        inputBar.toggle()
    }
}
