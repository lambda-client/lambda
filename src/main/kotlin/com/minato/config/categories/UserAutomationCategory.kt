
package com.minato.config.categories

import com.minato.Minato.mapper
import com.minato.config.ConfigCategory
import com.minato.config.automation.UserAutomationConfig
import com.minato.module.ModuleRegistry.moduleNameMap
import com.minato.util.FileUtils.ifExists
import com.minato.util.FolderRegistry
import java.io.File

object UserAutomationCategory : ConfigCategory() {
    override val name get() = "custom_automation"
    override val primaryFile: File = FolderRegistry.config.resolve("$name.json").toFile()

    override fun internalTryLoad() {
        primaryFile.ifExists {
            mapper.readTree(it)
                .takeIf { it.isObject }
                ?.asObject()
                ?.propertyStream()
                ?.forEach { (key, _) ->
                    if (configs.any { config -> config.name == key }) return@forEach
                    if (key == "_schemaVersion") return@forEach
                    UserAutomationConfig(key)
                }
        }
        super.internalTryLoad()
        configs.forEach {
            val config = it as? UserAutomationConfig ?: throw IllegalStateException("UserAutomationConfigs contains non-UserAutomationConfig")
            config.linkedModules.value.forEach { moduleName ->
                moduleNameMap[moduleName]?.automationConfig = config
            }
        }
    }
}