
package com.minato.util.text

import com.minato.Minato.mc

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