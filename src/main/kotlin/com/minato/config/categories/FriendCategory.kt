
package com.minato.config.categories

import com.minato.config.ConfigCategory
import com.minato.util.FolderRegistry
import java.io.File

object FriendCategory : ConfigCategory() {
	override val name get() = "friends"
	override val primaryFile: File = FolderRegistry.config.resolve("$name.json").toFile()
}
