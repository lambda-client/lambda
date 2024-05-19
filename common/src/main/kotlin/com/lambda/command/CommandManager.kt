package com.lambda.command

import com.lambda.brigadier.CommandException
import com.lambda.command.CommandRegistry.prefix
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Communication
import com.lambda.util.Communication.logError
import com.lambda.util.text.*
import com.lambda.util.text.ClickEvents.suggestCommand
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.command.CommandSource
import java.awt.Color
import kotlin.math.max
import kotlin.math.min


object CommandManager {
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
                dispatcher.execute(reader, player.commandSource)
            } catch (syntax: CommandSyntaxException) {
                createFeedback(syntax, reader)
            } catch (e: CommandException) {
                this@CommandManager.logError(e.info)
            }
        }
    }

    fun isCommand(message: String) =
        with(StringReader(message)) {
            canRead() && (peek() == prefix || peek() == '/')
        }

    fun String.isLambdaCommand() =
        with(StringReader(this)) {
            canRead() && peek() == prefix
        }

    fun currentDispatcher(message: String): CommandDispatcher<CommandSource> {
        return if (message.isLambdaCommand()) {
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

        this@CommandManager.logError(debugMessage)
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
                    styled(color = Communication.LogLevel.ERROR.logoColor, underlined = true) {
                        literal(syntax.input.substring(position))
                    }
                }
                styled(color = Communication.LogLevel.ERROR.logoColor, italic = true) {
                    translatable("command.context.here")
                }
            }
        })
    }
}
