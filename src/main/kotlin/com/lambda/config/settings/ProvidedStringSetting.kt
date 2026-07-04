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
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class ProvidedStringSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<ProvidedStringSetting, String>,
    defaultValue: String,
    visibility: () -> Boolean,
    private val supplier: () -> Array<String>
) : Setting<String>(name, description, defaultValue, layer, config, visibility) {
    override fun ImGuiBuilder.buildLayout() {
        val options = supplier()
        combo("##$name", preview = "$name: $value") {
            options.forEach { option ->
                val isSelected = value == option
                selectable(option, isSelected) {
                    if (!isSelected) value = option
                }
            }
        }

        lambdaTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            suggests { _, builder ->
                supplier().forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            executeWithResult {
                val newValue = parameter().value()
                if (newValue !in supplier()) return@executeWithResult failure("Invalid value")
                trySetValue(newValue)
                success()
            }
        }
    }
}
