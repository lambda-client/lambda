
package com.minato.config.settings.numeric


import com.minato.brigadier.argument.double
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
class DoubleSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<Double>, Double>,
	visibility: () -> Boolean,
	defaultValue: Double,
	override var range: ClosedRange<Double>,
	override var step: Double,
	unit: String
) : NumericSetting<Double>(name, description, config, layer, defaultValue, visibility, range, step, unit) {
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
		required(double(name, range.start, range.endInclusive)) { parameter ->
			execute {
				trySetValue(parameter().value())
			}
		}
	}
}