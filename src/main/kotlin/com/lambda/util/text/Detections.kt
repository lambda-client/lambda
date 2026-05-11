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

package com.lambda.util.text

import com.lambda.Lambda.mc

val playerRegex =  "^<(.+)>".toRegex()

interface Detector {
	fun matches(input: CharSequence): Boolean
}

interface RemovableDetector {
	fun removedOrNull(input: CharSequence): CharSequence?
}

interface PlayerDetector {
	fun playerName(input: CharSequence): String?
}

interface RegexDetector : Detector, RemovableDetector {
	val regexes: Array<out Regex>

	fun result(input: CharSequence) = regexes.find { it.containsMatchIn(input) }

	override fun matches(input: CharSequence) = regexes.any { it.containsMatchIn(input) }

	override fun removedOrNull(input: CharSequence) =
		result(input)
			?.replace(input, "")
			?.takeIf { it.isNotBlank() }
}

object MessageParser : PlayerDetector, RemovableDetector {
	override fun playerName(input: CharSequence) =
		playerRegex.find(input)?.value?.drop(1)?.dropLast(1)

	override fun removedOrNull(input: CharSequence) =
		input.replace(playerRegex, "")
}


enum class MessageType : Detector, PlayerDetector, RemovableDetector {
	Self {
		override fun matches(input: CharSequence) =
			input.startsWith("<${mc.gameProfile.name}>")

		override fun playerName(input: CharSequence): String? =
			mc.gameProfile.name
	},
	Others {
		override fun matches(input: CharSequence) = playerName(input) != null

		override fun playerName(input: CharSequence) =
			playerRegex.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() && it != name }
	},
	Both {
		override fun matches(input: CharSequence) = input.contains(playerRegex)

		override fun playerName(input: CharSequence) =
			playerRegex.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
	};

	override fun removedOrNull(input: CharSequence) =
		playerName(input)?.let { input.removePrefix("<$it>") }
}

enum class DirectMessage(override vararg val regexes: Regex) : RegexDetector, PlayerDetector {
	Send("^To (.+?): ".toRegex(RegexOption.IGNORE_CASE)),
	Receive(
		"^(.+?) whispers( to you)?: ".toRegex(),
		"^\\[?(.+?)( )?->( )?.+?]?( )?:? ".toRegex(),
		"^From (.+?): ".toRegex(RegexOption.IGNORE_CASE),
		"^. (.+?) » .w+? » ".toRegex()
	),
	Both(*Send.regexes, *Receive.regexes);

	override fun playerName(input: CharSequence) =
		result(input)?.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}