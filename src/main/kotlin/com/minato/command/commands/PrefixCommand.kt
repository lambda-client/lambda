
package com.minato.command.commands

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.greedyString
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.CommandRegistry
import com.minato.command.MinatoCommand
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import com.minato.util.text.buildText
import com.minato.util.text.literal

@Suppress("unused")
object PrefixCommand : MinatoCommand(
	"prefix",
	usage = "prefix <prefix>",
	description = "Sets the prefix for Minato commands. If the prefix does not seem to work, try putting it in double quotes."
) {
	// i have no idea why someone would want to use some of these as a prefix
	// but ig the people who run 20 clients at once could benefit from this
	val pattern = Regex("^[!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~]$")

	override fun CommandBuilder.create() {
		required(greedyString("prefix")) { prefixStr ->
			executeWithResult {
				val prefix = prefixStr().value()
				if (!pattern.matches(prefix)) {
					return@executeWithResult failure("Prefix must be a single non-alphanumeric ASCII character, excluding spaces.")
				}
				val prefixChar = prefix.first()
				@Suppress("unchecked_cast")
				CommandRegistry.prefixSetting.trySetValue(prefixChar)
				return@executeWithResult success()
			}
		}

		execute {
			info(
				buildText {
					literal("The prefix is currently: ${CommandRegistry.prefix}")
				}
			)
		}
	}
}