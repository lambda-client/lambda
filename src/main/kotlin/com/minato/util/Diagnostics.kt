
package com.minato.util

import com.minato.module.ModuleRegistry.modules

object Diagnostics {
    // ToDo: Expand this to include more information like version, etc.
    fun gatherDiagnostics() = buildString {
        modules.filter { it.isEnabled }
            .forEach { module ->
                append("\t${module.name}")
                module.settingLayers.forEachEntry { path, single ->
                    val setting = single.entry
                    if (setting.isModified) {
                        append("\t\t${path.joinToString(".", postfix = ".") { it.name }}${setting.name} -> ${setting.value}")
                    }
                }
            }
    }
}