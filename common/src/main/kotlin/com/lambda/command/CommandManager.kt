package com.lambda.command

import com.lambda.core.Loadable
import com.lambda.brigadier.CommandException
import com.lambda.brigadier.register
import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Communication
import com.lambda.util.Communication.logError
import com.lambda.util.text.*
import com.lambda.util.text.ClickEvents.suggestCommand
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.command.CommandSource
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.awt.Color
import kotlin.math.max
import kotlin.math.min


object CommandManager : Configurable(LambdaConfig), Loadable {
    override val name = "command"

    val prefix by setting("prefix", ';')

    val commands = mutableSetOf<LambdaCommand>()
    val dispatcher by lazy { CommandDispatcher<CommandSource>() }
    private const val ERROR_PADDING = 10

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

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .forPackage("com.lambda.command.commands")
                .setScanners(Scanners.SubTypes)
        ).getSubTypesOf(LambdaCommand::class.java).forEach { commandClass ->
            commandClass.declaredFields.find {
                it.name == "INSTANCE"
            }?.apply {
                isAccessible = true
                (get(null) as? LambdaCommand)?.let { command ->
                    commands.add(command)
                }
            }
        }

        return "Registered ${commands.size} commands"
    }
}
