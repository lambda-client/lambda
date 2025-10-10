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
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.config.groups.SettingGroup
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import imgui.flag.ImGuiInputTextFlags
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.lambda.config.Configurable]
 */
class StringSetting(
    override var name: String,
    defaultValue: String,
    var multiline: Boolean = false,
    var flags: Int = ImGuiInputTextFlags.None,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<String>(
    name,
    defaultValue,
    TypeToken.get(String::class.java).type,
    description,
    visibility
) {
    override fun ImGuiBuilder.buildLayout() {
        if (multiline) {
            inputTextMultiline(name, ::value, flags = flags)
        } else {
            inputText(name, ::value, flags)
        }
        lambdaTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(greedyString(name)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }

    @SettingGroup.SettingEditorDsl
    @Suppress("unchecked_cast")
    fun SettingGroup.TypedEditBuilder<String>.multiline(multiline: Boolean) {
        (settings as Set<StringSetting>).forEach { it.multiline = multiline }
    }

    @SettingGroup.SettingEditorDsl
    @Suppress("unchecked_cast")
    fun SettingGroup.TypedEditBuilder<String>.flags(flags: Int) {
        (settings as Set<StringSetting>).forEach { it.flags = flags }
    }
}
