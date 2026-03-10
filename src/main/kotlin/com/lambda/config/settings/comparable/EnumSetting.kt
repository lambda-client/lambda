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

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.Describable
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.extension.displayValue
import net.minecraft.command.CommandRegistryAccess
import kotlin.properties.Delegates

/**
 * @see [com.lambda.config.Configurable]
 */
class EnumSetting<T : Enum<T>>(defaultValue: T) : SettingCore<T>(
	defaultValue,
	TypeToken.get(defaultValue.declaringJavaClass).type
) {
    var index by Delegates.observable(value.ordinal) { _, _, to ->
        value = value.enumValues[to % value.enumValues.size]
    }

	context(setting: Setting<*, T>)
    override fun loadFromJson(serialized: JsonElement) {
        super.loadFromJson(serialized)
        index = value.ordinal // super bug fix for imgui
    }

	context(setting: Setting<*, T>)
    override fun ImGuiBuilder.buildLayout() {
        val values = value.enumValues
        val currentDisplay = value.displayValue

        combo("##${setting.name}", preview = "${setting.name}: $currentDisplay") {
            values.forEachIndexed { idx, v ->
                val isSelected = idx == index

                selectable(v.displayValue, isSelected) {
                    if (!isSelected) index = idx
                }

                (v as? Describable)?.let { lambdaTooltip(it.description) }
            }
        }

        lambdaTooltip(setting.description)
    }

	context(setting: Setting<*, T>)
    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(setting.name)) { parameter ->
            suggests { _, builder ->
                value.enumValues.forEach { builder.suggest(it.name.capitalize()) }
                builder.buildFuture()
            }
            executeWithResult {
                val newValue = value.enumValues.find { it.name.equals(parameter().value(), true) }
                    ?: return@executeWithResult failure("Invalid value")
                setting.trySetValue(newValue)
                return@executeWithResult success()
            }
        }
    }

    companion object {
        val <T : Enum<T>> T.enumValues: Array<T> get() =
            declaringJavaClass.enumConstants
    }
}
