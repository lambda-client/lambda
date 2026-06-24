/*
 * Copyright 2026 Lambda
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

package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.CommandRegistry
import com.lambda.command.LambdaCommand
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.literal

@Suppress("unused")
object PrefixCommand : LambdaCommand(
	"prefix",
	usage = "prefix <prefix>",
	description = "Sets the prefix for Lambda commands. If the prefix does not seem to work, try putting it in double quotes."
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