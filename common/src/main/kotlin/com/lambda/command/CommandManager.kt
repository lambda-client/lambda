package com.lambda.command

import com.lambda.Lambda.LOG
import com.lambda.LambdaConfig
import com.lambda.Loadable
import com.lambda.brigadier.*
import com.lambda.config.Configurable
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
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
import kotlin.math.max
import kotlin.math.min


object CommandManager : Configurable(LambdaConfig), Loadable {
    override val name = "command"

    val prefix by setting("prefix", ';')

    private val commands = mutableSetOf<LambdaCommand>()
    private val dispatcher by lazy { CommandDispatcher<CommandSource>() }
    private const val ERROR_PADDING = 10
    private val errorColor = Color.RED

    fun register(command: String, vararg alias: String, action: LiteralArgumentBuilder<CommandSource>.() -> Unit) {
        (listOf(command) + alias).forEach {
            dispatcher.register(it, action)
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

    fun executeCommand(command: String) {
        runSafe {
            val isolatedCommand = command.drop(1)

            if (isolatedCommand.isBlank()) return@runSafe
            val reader = StringReader(isolatedCommand)
            mc.inGameHud.chatHud.addToMessageHistory(command)

            try {
                dispatcher.execute(reader, player.commandSource)
            } catch (syntax: CommandSyntaxException) {
                createFeedback(syntax, reader)
            } catch (e: CommandException) {
                player.sendMessage(buildText {
                    color(errorColor) {
                        text(e.info)
                    }
                })
            }
        }
    }

    private fun SafeContext.createFeedback(
        syntax: CommandSyntaxException,
        reader: StringReader,
    ) {
        val debugMessage = syntax.message ?: return

        player.sendMessage(buildText {
            color(errorColor) {
                literal(debugMessage)
            }
        })
        if (syntax.input == null || syntax.cursor < 0) {
            return
        }
        val position = min(syntax.input.length, syntax.cursor)
        player.sendMessage(buildText {
            clickEvent(suggestCommand("$prefix${reader.string}")) {
                color(Color.GREY) {
                    if (position > ERROR_PADDING) {
                        literal("...")
                    }
                    literal(syntax.input.substring(max(0, (position - ERROR_PADDING)), position))
                }
                if (position < syntax.input.length) {
                    styled(color = errorColor, underlined = true) {
                        literal(syntax.input.substring(position))
                    }
                }
                styled(color = errorColor, italic = true) {
                    translatable("command.context.here")
                }
            }
        })
    }

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("com.lambda.command.commands"))
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