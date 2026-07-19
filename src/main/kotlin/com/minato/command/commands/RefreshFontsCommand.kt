
package com.minato.command.commands

import com.minato.brigadier.execute
import com.minato.command.MinatoCommand
import com.minato.graphics.text.FontHandler
import com.minato.util.extension.CommandBuilder

object RefreshFontsCommand : MinatoCommand(
	name = "refresh_fonts",
	usage = "refresh_fonts",
	description = "refreshes the font cache"
) {
	override fun CommandBuilder.create() {
		execute {
			FontHandler.discoverFonts()
		}
	}
}