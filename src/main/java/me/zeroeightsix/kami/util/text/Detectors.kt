package me.zeroeightsix.kami.util.text

import me.zeroeightsix.kami.KamiMod

interface Detector {
    infix fun detect(input: CharSequence): Boolean

    infix fun detectNot(input: CharSequence) = !detect(input)
}

interface RemovableDetector {
    fun removedOrNull(input: CharSequence): CharSequence?
}

interface PlayerDetector {
    fun playerName(input: CharSequence): String?
}

interface PrefixDetector : Detector, RemovableDetector {
    val prefixes: Array<out CharSequence>

    override fun detect(input: CharSequence) = prefixes.any { input.startsWith(it) }

    override fun removedOrNull(input: CharSequence) = prefixes.firstOrNull(input::startsWith)?.let {
        input.removePrefix(it)
    } ?: run {
        KamiMod.LOG.debug("Could not replace type ${this.javaClass.simpleName} for message \"$input\"")
        null
    }
}

interface RegexDetector : Detector, RemovableDetector {
    val regexes: Array<out Regex>

    override infix fun detect(input: CharSequence) = regexes.any { it.containsMatchIn(input) }

    fun matchedRegex(input: CharSequence) = regexes.find { it.containsMatchIn(input) }

    override fun removedOrNull(input: CharSequence): CharSequence? = matchedRegex(input)?.let { regex ->
        input.replace(regex, "").takeIf { it.isNotBlank() }
    } ?: run {
        KamiMod.LOG.debug("Could not replace type ${this.javaClass.simpleName} for message \"$input\"")
        null
    }
}