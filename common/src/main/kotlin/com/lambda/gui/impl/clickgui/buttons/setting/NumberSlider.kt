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

import com.lambda.config.settings.NumericSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.InputBarOverlay
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.Mouse
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.MathUtils.typeConvert
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import com.lambda.util.math.normalize

class NumberSlider<N>(
    setting: NumericSetting<N>,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : Slider<N, NumericSetting<N>>(
    setting, owner
) where N : Number, N : Comparable<N> {
    private val doubleRange get() = setting.range.let { it.start.toDouble()..it.endInclusive.toDouble() }
    override val progress get() = doubleRange.normalize(value.toDouble())

    private val layer = ChildLayer.Drawable(owner.gui, this, owner.renderer, ::rect, InputBarOverlay::isActive)
    private val inputBar: InputBarOverlay = object : InputBarOverlay(renderer, layer) {
        override val pressAnimation get() = this@NumberSlider.pressAnimation
        override val interactAnimation get() = this@NumberSlider.interactAnimation
        override val hoverFontAnimation get() = this@NumberSlider.hoverFontAnimation
        override val showAnimation get() = this@NumberSlider.showAnimation

        override fun isCharAllowed(string: String, char: Char): Boolean {
            return when (char) {
                '.' -> char !in string
                '-' -> string.isEmpty()
                else -> char.isDigit()
            }
        }

        override fun getText() = "$setting".replace(',', '.') // "0,0".toDouble() is null
        override fun setStringValue(string: String) {
            string.toDoubleOrNull()?.let(::setValue)
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
        if (e.button != Mouse.Button.Right) return
        if (!inputBar.isActive) (owner.gui as? AbstractClickGui)?.unfocusSettings()
        inputBar.toggle()
    }

    override fun slide() {
        if (!inputBar.isActive) super.slide()
    }

    override fun setValueByProgress(progress: Double) {
        setValue(
            lerp(
                progress,
                setting.range.start.toDouble(),
                setting.range.endInclusive.toDouble()
            )
        )
    }

    private fun setValue(valueIn: Double) {
        value = value.typeConvert(valueIn.roundToStep(setting.step.toDouble()))
    }
}
