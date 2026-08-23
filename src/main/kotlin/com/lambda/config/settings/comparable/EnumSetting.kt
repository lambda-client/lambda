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

package com.lambda.config.settings.comparable

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.Describable
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.extension.displayValue
import net.minecraft.command.CommandRegistryAccess

class EnumSetting<T : Enum<T>>(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<EnumSetting<T>, T>,
    visibility: () -> Boolean,
    defaultValue: T
) : Setting<T>(name, description, defaultValue, layer, config, visibility) {
    override fun ImGuiBuilder.buildLayout() {
        val values = value.enumValues
        val currentDisplay = value.displayValue
        val currentIndex = value.ordinal

        combo("##$name", preview = "$name: $currentDisplay") {
            values.forEachIndexed { idx, v ->
                val isSelected = idx == currentIndex

                selectable(v.displayValue, isSelected) {
                    if (!isSelected) value = values[idx % values.size]
                }

                (v as? Describable)?.let { lambdaTooltip(it.description) }
            }
        }

        lambdaTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            suggests { _, builder ->
                value.enumValues.forEach { builder.suggest(it.name.capitalize()) }
                builder.buildFuture()
            }
            executeWithResult {
                val newValue = value.enumValues.find { it.name.equals(parameter().value(), true) }
                    ?: return@executeWithResult failure("Invalid value")
                trySetValue(newValue)
                return@executeWithResult success()
            }
        }
    }

    private val <T : Enum<T>> T.enumValues: Array<T>
        get() = declaringJavaClass.enumConstants
}