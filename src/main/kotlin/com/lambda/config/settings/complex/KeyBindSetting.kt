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

package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.gui.widgets.KeybindWidget
import com.lambda.util.KeyCode
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class KeyBindSetting(
    override val name: String,
    defaultValue: KeyCode,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<KeyCode>(
    defaultValue,
    TypeToken.get(KeyCode::class.java).type,
    description,
    visibility
) {
    private val widget = KeybindWidget(
        label = name,
        description = description,
        valueGetter = { value },
        valueSetter = { value = it },
    )

    override fun ImGuiBuilder.buildLayout() {
        with(widget) { build() }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            suggests { _, builder ->
                KeyCode.entries.forEach { builder.suggest(it.name.capitalize()) }
                builder.buildFuture()
            }
            execute {
                trySetValue(KeyCode.valueOf(parameter().value()))
            }
        }
    }
}
