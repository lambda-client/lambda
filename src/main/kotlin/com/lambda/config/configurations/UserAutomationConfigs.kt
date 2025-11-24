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

package com.lambda.config.configurations

import com.google.gson.JsonParser
import com.lambda.config.Configuration
import com.lambda.config.UserAutomationConfig
import com.lambda.module.ModuleRegistry.moduleNameMap
import com.lambda.util.FileUtils.ifExists
import com.lambda.util.FolderRegister
import java.io.File

object UserAutomationConfigs : Configuration() {
    override val configName = "custom-automation"
    override val primary: File = FolderRegister.config.resolve("${configName}.json").toFile()

    override fun internalTryLoad() {
        primary.ifExists {
            JsonParser.parseReader(it.reader()).asJsonObject.entrySet().forEach { (name, _) ->
                if (configurables.any { config -> config.name == name }) return@forEach
                UserAutomationConfig(name)
            }
        }
        super.internalTryLoad()
        configurables.forEach {
            val config = it as? UserAutomationConfig ?: throw IllegalStateException("UserAutomationConfigs contains non-AutomationConfig")
            config.linkedModules.value.forEach { moduleName ->
                moduleNameMap[moduleName]?.automationConfig = config
            }
        }
    }
}