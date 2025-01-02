/*
 * Copyright 2024 Lambda
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

import com.lambda.config.Configuration
import com.lambda.config.configurations.ModuleConfig.configName
import com.lambda.config.configurations.ModuleConfig.primary
import com.lambda.util.FolderRegister
import java.io.File


/**
 * The [ModuleConfig] object represents the configuration file for the [Module]s.
 *
 * This object is used to save and load the settings of all [Module]s in the system.
 *
 * @property configName The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 */
object ModuleConfig : Configuration() {
    override val configName get() = "modules"
    override val primary: File = FolderRegister.config.resolve("$configName.json").toFile()
}
