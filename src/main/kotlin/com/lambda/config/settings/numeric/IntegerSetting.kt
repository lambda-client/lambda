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

import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.config.settings.NumericSetting
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import java.math.BigDecimal
import java.math.RoundingMode

@ConfigEntryDsl
fun Config.intSetting(
	name: String,
	defaultValue: Int,
	range: ClosedRange<Int>,
	step: Int = 1,
	description: String = "",
	unit: String = "",
	visibility: () -> Boolean = { true }
) = setting(name) { layer -> IntegerSetting(name, description, this, layer, visibility, defaultValue, range, step, unit) }

/**
 * @see [com.lambda.config.Config]
 */
class IntegerSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<Int>, Int>,
	visibility: () -> Boolean,
	defaultValue: Int,
	override var range: ClosedRange<Int>,
	override var step: Int = 1,
	unit: String
) : NumericSetting<Int>(name, description, config, layer, defaultValue, visibility, range, step, unit) {
	override fun convert(number: BigDecimal) =
		number.setScale(0, RoundingMode.HALF_UP).toInt()

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(integer(name, range.start, range.endInclusive)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }
}