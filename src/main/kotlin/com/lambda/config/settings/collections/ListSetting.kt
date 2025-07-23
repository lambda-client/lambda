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

package com.lambda.config.settings.collections

import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import imgui.ImGuiTextFilter
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import java.lang.reflect.Type

/**
 * @see [com.lambda.config.Configurable]
 */
class ListSetting<T : Any>(
    override val name: String,
    private val defaultValue: MutableList<T>,
    type: Type,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<MutableList<T>>(
    defaultValue,
    type,
    description,
    visibility
) {
    override val layout: ImGuiBuilder.() -> Unit
        get() =
        {
            filter("##Filter") { f ->
                defaultValue
                    .filter { f.passFilter(it.toString()) }
                    .forEach {
                        selectable(it.toString(), value.contains(it)) { value.add(it) }
                    }
            }
        }
}
