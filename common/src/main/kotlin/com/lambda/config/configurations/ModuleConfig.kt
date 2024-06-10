package com.lambda.config.configurations

import com.lambda.config.Configuration


/**
 * The [ModuleConfig] object represents the configuration file for the [Module]s.
 *
 * This object is used to save and load the settings of all [Module]s in the system.
 */
object ModuleConfig : Configuration(configName = "modules")
