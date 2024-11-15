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

import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.extension.displayValue
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import com.lambda.util.math.transform
import java.awt.Color

class EnumSlider<T : Enum<T>>(
    setting: EnumSetting<T>,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : Slider<T, EnumSetting<T>>(setting, owner) {
    private val values = setting.enumValues
    private val enumSize = values.size

    override val progress get() = transform(value.ordinal.toDouble(), 0.0, enumSize - 1.0, 0.0, 1.0)
    private var valueSetByDrag = false

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.Render) {
            // Enum entry name
            renderer.font.apply {
                val text = value.displayValue
                val progress = 1.0 - activeAnimation
                val scale = lerp(progress, 0.5, 1.0)
                val width = getWidth(text, scale)
                val position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + width, 0.0)
                val color = Color.WHITE.setAlpha(lerp(showAnimation, 0.0, progress))

                build(text, position, color, scale)
            }
        }
    }

    override fun setValueByProgress(progress: Double) {
        val entryIndex = (progress * enumSize).floorToInt().coerceIn(0, enumSize - 1)
        value = values[entryIndex]
        valueSetByDrag = true
    }

    override fun onPress(e: GuiEvent.MouseClick) {
        valueSetByDrag = false
    }

    override fun onRelease(e: GuiEvent.MouseClick) {
        if (valueSetByDrag) return
        playClickSound()
        setting.next()
    }
}
