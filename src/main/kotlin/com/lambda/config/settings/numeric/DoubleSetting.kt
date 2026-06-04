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

package com.lambda.config.settings.numeric


import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.SettingLayer
import com.lambda.config.settings.NumericSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.type.ImInt
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.math.MathUtils.roundToStep
import net.minecraft.command.CommandRegistryAccess
import kotlin.math.roundToInt

/**
 * @see [com.lambda.config.Config]
 */
class DoubleSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingLayer.Single<*, Double>,
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