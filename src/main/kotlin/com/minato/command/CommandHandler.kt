
package com.minato.command

import com.minato.brigadier.CommandException
import com.minato.command.CommandRegistry.prefix
import com.minato.context.SafeContext
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils
import com.minato.util.CommunicationUtils.logError
import com.minato.util.text.ClickEvents.suggestCommand
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.color
import com.minato.util.text.literal
import com.minato.util.text.styled
import com.minato.util.text.translatable
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.command.CommandSource
import java.awt.Color
import kotlin.math.max
import kotlin.math.min


object CommandHandler {
    private const val ERROR_PADDING = 10

    val dispatcher by lazy { CommandDispatcher<CommandSource>() }

    fun executeCommand(command: String) {
        runSafe {
            val isolatedCommand = command.drop(1)

            if (isolatedCommand.isBlank()) return@runSafe
            mc.inGameHud.chatHud.addToMessageHistory(command)
            mc.commandHistoryManager.add(command)
            val reader = StringReader(isolatedCommand)

            try {
                dispatcher.execute(reader, connection.commandSource)
            } catch (syntax: CommandSyntaxException) {
                createFeedback(syntax, reader)
            } catch (e: CommandException) {
                this@CommandHandler.logError(e.info)
            }
        }
    }

    fun isCommand(message: String) =
        with(StringReader(message)) {
            canRead() && (peek() == prefix || peek() == '/')
        }

    fun String.isMinatoCommand() =
        with(StringReader(this)) {
            canRead() && peek() == prefix
        }

    fun currentDispatcher(message: String): CommandDispatcher<out CommandSource> {
        return if (message.isMinatoCommand()) {
            dispatcher
        } else {
            runSafe {
                connection.commandDispatcher
            } ?: throw IllegalStateException("Command dispatcher is not initialized")
        }
    }

    private fun SafeContext.createFeedback(
        syntax: CommandSyntaxException,
        reader: StringReader,
    ) {
        val debugMessage = syntax.message ?: return

        this@CommandHandler.logError(debugMessage)
        if (syntax.input == null || syntax.cursor < 0) {
            return
        }
        val position = min(syntax.input.length, syntax.cursor)
        player.sendMessage(buildText {
            clickEvent(suggestCommand("$prefix${reader.string}")) {
                color(Color.GRAY) {
                    if (position > ERROR_PADDING) {
                        literal("...")
                    }
                    literal(syntax.input.substring(max(0, (position - ERROR_PADDING)), position))
                }
                if (position < syntax.input.length) {
                    styled(color = CommunicationUtils.LogLevel.Error.logoColor, underlined = true) {
                        literal(syntax.input.substring(position))
                    }
                }
                styled(color = CommunicationUtils.LogLevel.Error.logoColor, italic = true) {
                    translatable("command.context.here")
                }
            }
        }, false)
    }
}
