
package com.minato.config.settings

import com.minato.config.Config
import com.minato.config.ConfigEditor
import com.minato.config.ConfigEditorD5l
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.ImGui.calcTextSize
import com.lambda.imgui.ImGui.dummy
import com.lambda.imgui.flag.ImGuiCol
import java.text.NumberFormat
import java.util.*

/**
 * @see [com.minato.config.Config]
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
		minatoTooltip(description)

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
