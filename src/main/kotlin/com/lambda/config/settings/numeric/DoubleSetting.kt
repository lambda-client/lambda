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

package com.lambda.config.settings.numeric


import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Setting
import com.lambda.config.settings.NumericSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.math.MathUtils.roundToStep
import net.minecraft.command.CommandRegistryAccess
import kotlin.math.roundToInt

/**
 * @see [com.lambda.config.Configurable]
 */
class DoubleSetting(
	defaultValue: Double,
	override var range: ClosedRange<Double>,
	override var step: Double,
	unit: String
) : NumericSetting<Double>(
	defaultValue,
	range,
	step,
	unit
) {
	private var valueIndex: Int
		get() = ((value - range.start) / step).roundToInt()
		set(index) {
			value = (range.start + index * step)
				.roundToStep(step)
				.coerceIn(range)
		}

	context(setting: Setting<*, Double>)
	override fun ImGuiBuilder.buildSlider() {
		val maxIndex = ((range.endInclusive - range.start) / step).toInt()
		slider("##${setting.name}", ::valueIndex, 0, maxIndex, "")
	}

	context(setting: Setting<*, Double>)
	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(double(setting.name, range.start, range.endInclusive)) { parameter ->
			execute {
				setting.trySetValue(parameter().value())
			}
		}
	}
}
