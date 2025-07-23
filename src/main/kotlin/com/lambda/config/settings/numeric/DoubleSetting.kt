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
import com.lambda.config.settings.NumericSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.lambda.config.Configurable]
 */
class DoubleSetting(
    override val name: String,
    defaultValue: Double,
    override val range: ClosedRange<Double>,
    override val step: Double,
    description: String,
    visibility: () -> Boolean,
    unit: String,
) : NumericSetting<Double>(
    defaultValue,
    range,
    step,
    description,
    visibility,
    unit
) {
    override val layout: ImGuiBuilder.() -> Unit
        get() =
        {
            inputDouble(name, ::value)
            sameLine()
            helpMarker(description)
        }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(double(name, range.start, range.endInclusive)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }
}
