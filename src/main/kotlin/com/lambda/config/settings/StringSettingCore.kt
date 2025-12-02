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
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.config.SettingEditorDsl
import com.lambda.config.SettingGroupEditor
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import imgui.flag.ImGuiInputTextFlags
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.lambda.config.Configurable]
 */
class StringSettingCore(
    defaultValue: String,
    var multiline: Boolean = false,
    var flags: Int = ImGuiInputTextFlags.None,
) : SettingCore<String>(
	defaultValue,
	TypeToken.get(String::class.java).type
) {
	context(setting: Setting<*, String>)
    override fun ImGuiBuilder.buildLayout() {
        if (multiline) {
            inputTextMultiline(setting.name, ::value, flags = flags)
        } else {
            inputText(setting.name, ::value, flags)
        }
        lambdaTooltip(setting.description)
    }

	context(setting: Setting<*, String>)
    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(greedyString(setting.name)) { parameter ->
            execute {
                setting.trySetValue(parameter().value())
            }
        }
    }

    companion object {
        @SettingEditorDsl
        @Suppress("unchecked_cast")
        fun SettingGroupEditor.TypedEditBuilder<String>.multiline(multiline: Boolean) {
            (settings as Collection<StringSettingCore>).forEach { it.multiline = multiline }
        }

        @SettingEditorDsl
        @Suppress("unchecked_cast")
        fun SettingGroupEditor.TypedEditBuilder<String>.flags(flags: Int) {
            (settings as Collection<StringSettingCore>).forEach { it.flags = flags }
        }
    }
}
