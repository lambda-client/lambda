/*
 * Copyright 2026 Lambda
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

import com.lambda.config.Config
import com.lambda.config.ConfigEditor
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGui.calcTextSize
import com.lambda.imgui.ImGui.dummy
import com.lambda.imgui.flag.ImGuiCol
import java.text.NumberFormat
import java.util.*

/**
 * @see [com.lambda.config.Config]
 */
abstract class NumericSetting<T>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<T>, T>,
	defaultValue: T,
	visibility: () -> Boolean,
	open var range: ClosedRange<T>,
	open var step: T,
	var unit: String
) : Setting<T>(name, description, defaultValue, layer, config, visibility) where T : Number, T : Comparable<T> {
	override var value: T
		get() = super.value
		set(newVal) {
			super.value = newVal.coerceIn(range)
		}

	private val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

	override fun toString() = "${formatter.format(value)}$unit"

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

	@Suppress("unchecked_cast", "unused")
	companion object {
		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.range(range: ClosedRange<T>) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.range = range }
		}

		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.step(step: T) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.step = step }
		}

		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.unit(unit: String) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.unit = unit }
		}
	}
}
