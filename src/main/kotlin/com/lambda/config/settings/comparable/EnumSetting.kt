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

package com.lambda.config.settings.comparable

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import imgui.ImColor
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiSliderFlags.AlwaysClamp
import net.minecraft.command.CommandRegistryAccess
import java.awt.Color
import kotlin.properties.Delegates

/**
 * @see [com.lambda.config.Configurable]
 */
class EnumSetting<T : Enum<T>>(
    override val name: String,
    defaultValue: T,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<T>(
    defaultValue,
    TypeToken.get(defaultValue.declaringJavaClass).type,
    description,
    visibility,
) {
    var index by Delegates.observable(value.ordinal) { _, _, to ->
        value = value.enumValues[to % value.enumValues.size]
    }

    override fun ImGuiBuilder.buildLayout() {
        text(name)

        sameLine()
        helpMarker(description)

        combo("##$name", ::index, value.enumValues.map { it.name.capitalize() }.toTypedArray())
        slider("##$name#", ::index, 0, value.enumValues.size - 1, format = "", flags = AlwaysClamp)

        val min = ImGui.getItemRectMin()
        val max = ImGui.getItemRectMax()
        val textSize = ImGui.calcTextSize(value.name)
        val center = ImVec2(
            (min.x + max.x) * 0.5f - textSize.x * 0.5f,
            (min.y + max.y) * 0.5f - textSize.y * 0.5f
        )

        windowDrawList.addText(
            center,
            ImColor.rgb(Color.WHITE),
            value.name,
        )
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

    companion object {
        val <T : Enum<T>> T.enumValues: Array<T> get() =
            declaringJavaClass.enumConstants
    }
}
