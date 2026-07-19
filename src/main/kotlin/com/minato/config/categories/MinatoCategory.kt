
package com.minato.config.categories

import com.minato.config.ConfigCategory
import com.minato.util.FolderRegistry
import java.io.File

object MinatoCategory : ConfigCategory() {
	override val name get() = "minato"
	override val primaryFile: File = FolderRegistry.config.resolve("$name.json").toFile()
}