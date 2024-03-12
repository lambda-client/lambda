package com.lambda.module

import com.lambda.config.Configuration
import com.lambda.module.ModuleConfig.configName
import com.lambda.module.ModuleConfig.primary
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
    override val configName = "modules"
    override val primary = File(FolderRegister.config, "$configName.json")
}