
package com.minato.config.automation

import com.minato.config.categories.UserAutomationCategory
import com.minato.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.minato.config.settings.collections.CollectionSetting.Companion.onSelect
import com.minato.module.Module
import com.minato.module.ModuleRegistry.moduleNameMap

class UserAutomationConfig(name: String) : AutomationConfig(name, UserAutomationCategory) {
    val linkedModules = setting<String>("Linked Modules", emptySet(), moduleNameMap.filter { it.value.defaultAutomationConfig != DEFAULT }.keys) { false }
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
		(automationConfig as? UserAutomationConfig)?.linkedModules?.value?.remove(name)
	}
}