package me.zeroeightsix.kami.util.text

import me.zeroeightsix.kami.command.CommandManager
import me.zeroeightsix.kami.module.modules.chat.EncryptChat
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.Wrapper
import org.kamiblue.commons.extension.replaceAll

object MessageDetection {
    enum class Command : PrefixDetector {
        KAMI_BLUE {
            override val prefixes: Array<out CharSequence>
                get() = arrayOf(CommandManager.prefix)
        },
        BARITONE {
            override val prefixes: Array<out CharSequence>
                get() = arrayOf(BaritoneUtils.prefix, "${CommandManager.prefix}b", ".b")
        },
        ANY_EXCEPT_DELIMITER {
            override val prefixes: Array<out CharSequence>
                get() = arrayOf("/", ",", ".", "-", ";", "?", "*", "^", "&", "#", "$", CommandManager.prefix)
        },
        ANY {
            override val prefixes: Array<out CharSequence>
                get() = arrayOf(*ANY_EXCEPT_DELIMITER.prefixes, EncryptChat.delimiter.value)
        }
    }

    enum class Message : Detector, PlayerDetector {
        SELF {
            override fun detect(input: CharSequence) = Wrapper.player?.name?.let {
                input.startsWith("<${it}>")
            } ?: false

            override fun playerName(input: CharSequence): String? {
                return if (detectNot(input)) null
                else Wrapper.player?.name
            }

            override fun removed(input: CharSequence): String? = Wrapper.player?.name?.let {
                if (detect(input)) {
                    input.toString().substring("<${it}>".length).takeIf { str -> str.isNotBlank() }
                } else {
                    null
                }
            }
        },
        OTHER {
            private val regex = "^<(\\w+)>".toRegex()

            override fun detect(input: CharSequence) = playerName(input) != null

            override fun playerName(input: CharSequence) = Wrapper.player?.name?.let { name ->
                regex.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() && it != name }
            }

            override fun removed(input: CharSequence): String? = if (detect(input)) {
                input.replace(regex, "").removePrefix(" ").takeIf { it.isNotBlank() }
            } else {
                null
            }
        },
        ANY {
            private val regex = "^<(\\w+)>".toRegex()

            override fun detect(input: CharSequence) = input.contains(regex)

            override fun playerName(input: CharSequence) =
                regex.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

            override fun removed(input: CharSequence): String? = if (detect(input)) {
                input.replace(regex, "").removePrefix(" ").takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    enum class Direct(override vararg val regexes: Regex) : RegexDetector, PlayerDetector {
        SENT("^To (\\w+?): ".toRegex(RegexOption.IGNORE_CASE)) {
            override fun removed(input: CharSequence): String? = if (detect(input)) {
                input.replaceAll("", *regexes).toString()
            } else {
                null
            }
        },
        RECEIVE(
            "^(\\w+?) whispers( to you)?: ".toRegex(),
            "^\\[?(\\w+?)( )?->( )?\\w+?]?( )?:? ".toRegex(),
            "^From (\\w+?): ".toRegex(RegexOption.IGNORE_CASE),
            ". (\\w+?) » \\w+? » ".toRegex()
        ) {
            override fun removed(input: CharSequence): String? = if (detect(input)) {
                input.replaceAll("", *regexes).toString()
            } else {
                null
            }
        },
        ANY(*SENT.regexes, *RECEIVE.regexes) {
            override fun removed(input: CharSequence): String? = when {
                SENT.detect(input) -> input.replaceAll("", *SENT.regexes).toString()
                RECEIVE.detect(input) -> input.replaceAll("", *RECEIVE.regexes).toString()
                else -> null
            }
        };

        override fun playerName(input: CharSequence) = matchedRegex(input)?.let { regex ->
            input.replace(regex, "$1").takeIf { it.isNotBlank() }
        }
    }

    enum class Server(override vararg val regexes: Regex) : RegexDetector {
        QUEUE("^Position in queue: ".toRegex()),
        QUEUE_IMPORTANT("^Position in queue: [1-5]$".toRegex()),
        RESTART("^\\[SERVER] Server restarting in ".toRegex()),
        ANY(*QUEUE.regexes, *RESTART.regexes)
    }

    enum class Other(override vararg val regexes: Regex) : RegexDetector {
        BARITONE("^\\[B(aritone)?]".toRegex()),
        TPA_REQUEST("^\\w+? (has requested|wants) to teleport to you\\.".toRegex())
    }
}
