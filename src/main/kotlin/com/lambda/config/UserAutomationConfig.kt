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

package com.lambda.config

import com.lambda.config.configurations.UserAutomationConfigs
import com.lambda.module.ModuleRegistry.moduleNameMap

class UserAutomationConfig(override val name: String) : AutomationConfig(name, UserAutomationConfigs) {
    val linkedModules = setting("Linked Modules", moduleNameMap.filter { it.value.defaultAutomationConfig != Companion.DEFAULT }.keys, emptySet())
        .onSelect { module -> moduleNameMap[module]?.automationConfig = this@UserAutomationConfig }
        .onDeselect { module ->
            moduleNameMap[module]?.let { module ->
                module.automationConfig = module.defaultAutomationConfig
            }
        }
}