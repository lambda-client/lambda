
package com.minato.config.settings.numeric

import com.minato.brigadier.argument.float
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.settings.NumericSetting
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.type.ImInt
import com.minato.util.extension.CommandBuilder
import com.minato.util.math.MathUtils.roundToStep
import net.minecraft.command.CommandRegistryAccess
import kotlin.math.roundToInt

/**
 * @see [com.minato.config.Config]
 */
class FloatSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<Float>, Float>,
	visibility: () -> Boolean,
	defaultValue: Float,
	override var range: ClosedRange<Float>,
	override var step: Float = 1f,
	unit: String,
) : NumericSetting<Float>(name, description, config, layer, defaultValue, visibility, range, step, unit) {
	override fun ImGuiBuilder.buildSlider() {
		val maxIndex = ((range.endInclusive - range.start) / step).toInt()
		val currentIndex = ((value - range.start) / step).roundToInt()
		val imInt = ImInt(currentIndex)
		slider("##$name", imInt, 0, maxIndex, "") {
			value = (range.start + imInt.get() * step)
				.roundToStep(step)
				.coerceIn(range)
		}
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(float(name, range.start, range.endInclusive)) { parameter ->
			execute {
				trySetValue(parameter().value())
			}
		}
	}
}