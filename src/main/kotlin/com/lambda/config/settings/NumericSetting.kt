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

package com.lambda.config.settings

import com.google.gson.reflect.TypeToken
import com.lambda.config.AbstractSetting
import com.lambda.config.AutomationConfig
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.ImGui
import imgui.ImGui.calcTextSize
import imgui.ImGui.dummy
import imgui.flag.ImGuiCol
import java.text.NumberFormat
import java.util.*
import kotlin.reflect.KProperty

/**
 * @see [com.lambda.config.Configurable]
 */
abstract class NumericSetting<T>(
    override var name: String,
    value: T,
    open var range: ClosedRange<T>,
    open var step: T,
    description: String,
    var unit: String,
    visibility: () -> Boolean
) : AbstractSetting<T>(
    name,
    value,
    TypeToken.get(value::class.java).type,
    description,
    visibility
) where T : Number, T : Comparable<T> {
    private val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

    override fun toString() = "${formatter.format(value)}$unit"

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn.coerceIn(range)
    }

    /**
     * Subclasses must implement this to provide their specific slider widget.
     */
    protected abstract fun ImGuiBuilder.buildSlider()

    override fun ImGuiBuilder.buildLayout() {
        val showReset = isModified
        val resetButtonText = "R"
        val valueString = this@NumericSetting.toString()

        buildSlider()
        lambdaTooltip(description)

        val itemRectMin = ImGui.getItemRectMin()
        val itemRectMax = ImGui.getItemRectMax()
        val textHeight = ImGui.getTextLineHeight()
        val textY = itemRectMin.y + (itemRectMax.y - itemRectMin.y - textHeight) / 2.0f
        val labelWidth = calcTextSize(name).x
        val valueWidth = calcTextSize(valueString).x

        val labelEndPosX = itemRectMin.x + style.framePadding.x * 2 + labelWidth
        val valueStartPosX = itemRectMax.x - style.framePadding.x * 2 - valueWidth

        windowDrawList.addText(itemRectMin.x + style.framePadding.x * 2, textY, ImGui.getColorU32(ImGuiCol.Text), name)
        if (labelEndPosX < valueStartPosX) {
            windowDrawList.addText(valueStartPosX, textY, ImGui.getColorU32(ImGuiCol.Text), valueString)
        }

        sameLine(0.0f, style.itemSpacing.x)
        if (showReset) {
            button("$resetButtonText##$name") {
                reset()
            }
            onItemHover {
                tooltip { text("Reset to default") }
            }
        } else {
            dummy(calcTextSize(resetButtonText).x + style.framePadding.x * 2.0f, ImGui.getFrameHeight())
        }
    }

    companion object {
        @AutomationConfig.SettingEditorDsl
        @Suppress("unchecked_cast")
        fun <T> AutomationConfig.TypedEditBuilder<T>.range(range: ClosedRange<T>) where T : Number, T : Comparable<T> {
            (settings as Collection<NumericSetting<T>>).forEach { it.range = range }
        }

        @AutomationConfig.SettingEditorDsl
        @Suppress("unchecked_cast")
        fun <T> AutomationConfig.TypedEditBuilder<T>.step(step: T) where T : Number, T : Comparable<T> {
            (settings as Collection<NumericSetting<T>>).forEach { it.step = step }
        }

        @AutomationConfig.SettingEditorDsl
        @Suppress("unchecked_cast")
        fun <T> AutomationConfig.TypedEditBuilder<T>.unit(unit: String) where T : Number, T : Comparable<T> {
            (settings as Collection<NumericSetting<T>>).forEach { it.unit = unit}
        }
    }
}
