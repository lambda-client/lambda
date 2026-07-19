
package com.minato.module

import com.minato.core.Loadable
import com.minato.util.ReflectionUtils.getInstances

object ModuleRegistry : Loadable {
    override val priority = 1

    val modules = getInstances<Module>()
        .sortedBy { it.name }

    val moduleNameMap = modules.associateBy { it.name }

    override fun load(): String {
        var settingCount = 0
        modules.forEach { module ->
            module.settingLayers.forEachEntry { _, _ -> settingCount++ }
        }
        return "Loaded ${modules.size} modules with $settingCount settings"
    }
}
