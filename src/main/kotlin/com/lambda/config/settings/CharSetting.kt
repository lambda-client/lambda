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

package com.lambda.config.settings

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.lambda.config.Configurable]
 */
class CharSetting(
    override val name: String,
    defaultValue: Char,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Char>(
    defaultValue,
    TypeToken.get(Char::class.java).type,
    description,
    visibility
) {
    override fun ImGuiBuilder.buildLayout() {

    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            executeWithResult {
                val char = parameter().value().firstOrNull() ?: return@executeWithResult failure("Cant parse char type")
                trySetValue(char)
                return@executeWithResult success()
            }
        }
    }
}
