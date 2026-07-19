
package com.minato.config.categories

import com.minato.config.ConfigCategory
import com.minato.config.categories.ModuleCategory.name
import com.minato.util.FolderRegistry
import java.io.File

/**
 * The [ModuleCategory] object represents the configuration file for the [Module]s.
 *
 * This object is used to save and load the settings of all [Module]s in the system.
 *
 * @property name The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 */
object ModuleCategory : ConfigCategory() {
	override val name get() = "modules"
	override val primaryFile: File = FolderRegistry.config.resolve("$name.json").toFile()
}