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
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry.moduleNameMap

class UserAutomationConfig(override val name: String) : AutomationConfig(name, UserAutomationConfigs) {
    val linkedModules = setting<String>("Linked Modules", emptySet(), moduleNameMap.filter { it.value.defaultAutomationConfig != Companion.DEFAULT }.keys)
        .onSelect { name ->
	        moduleNameMap[name]?.let {
		        it.removeLink()
		        it.automationConfig = this@UserAutomationConfig
	        }
        }

	    .onDeselect { name ->
		    moduleNameMap[name]?.let { module ->
			    module.automationConfig = module.defaultAutomationConfig
		    }
	    }

	private fun Module.removeLink() {
		(automationConfig as UserAutomationConfig).linkedModules.value -= name
	}
}