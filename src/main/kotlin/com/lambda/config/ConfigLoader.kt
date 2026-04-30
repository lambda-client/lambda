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

package com.lambda.config

import com.lambda.core.Loadable

@Suppress("unused")
object ConfigLoader: Loadable {
    val configCategories = mutableSetOf<ConfigCategory>()
    val configs: Set<Config>
        get() = configCategories.flatMapTo(mutableSetOf()) { it.configs }
    val settings: List<Setting<*, *>>
        get() = configs.flatMapTo(mutableListOf()) { it.settings }

    override fun load(): String {
        configCategories.forEach {
            it.tryLoad()
        }
        return "Loading ${configCategories.size} config categories"
    }

    fun configByName(name: String) =
        configs.find { it.name == name }

    fun configByCommandName(name: String) =
        configs.find { it.commandName == name }

    fun settingByCommandName(config: Config, name: String) =
        config.settings.find { it.commandName == name }
}