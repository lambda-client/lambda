package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.command.LambdaCommand
import com.lambda.config.Configuration
import com.lambda.core.Loader
import com.lambda.event.EventFlow
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.GuiSettings
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeGameConcurrent
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.text.*
import net.minecraft.client.toast.SystemToast
import net.minecraft.text.Text
import java.awt.Color

object Communication {
    val ascii = """
        ⣰⡛⠶⣄⠀⠀⠀⠀⠀⠀
        ⠑⠭⣛⡜⣳⡀⠀⠀⠀⠀
        ⠀⠀⠹⣾⣥⣛⡄⠀⠀⠀
        ⠀⠀⢠⣿⢯⣷⣻⡄⠀⠀
        ⠀⢠⣿⣿⣿⢶⣏⡿⡄⠀
        ⢠⣿⣿⡿⠃⠘⣿⣼⣻⣄
        ⠻⢿⡿⠁⠀⠀⠘⢷⡽⠞
    """.trimIndent()

    fun Any.debug(message: String, source: String = "") = log(LogLevel.DEBUG.text(message), LogLevel.DEBUG, source)
    fun Any.debug(message: Text, source: Text = Text.empty()) = log(message, LogLevel.DEBUG, textSource = source)
    fun Any.info(message: String, source: String = "") = log(LogLevel.INFO.text(message), LogLevel.INFO, source)
    fun Any.info(message: Text, source: Text = Text.empty()) = log(message, LogLevel.INFO, textSource = source)
    fun Any.warn(message: String, source: String = "") = log(LogLevel.WARN.text(message), LogLevel.WARN, source)
    fun Any.warn(message: Text, source: Text = Text.empty()) = log(message, LogLevel.WARN, textSource = source)
    fun Any.logError(message: String, source: String = "") = log(LogLevel.ERROR.text(message), LogLevel.ERROR, source)
    fun Any.logError(message: Text, source: Text = Text.empty()) = log(message, LogLevel.ERROR, textSource = source)
    fun Any.logError(message: String, throwable: Throwable) = logError(message, throwable.message ?: "")

    fun Any.toast(message: String, logLevel: LogLevel = LogLevel.INFO) {
        toast(logLevel.text(message), logLevel)
    }

    fun Any.toast(message: Text, logLevel: LogLevel = LogLevel.INFO) {
        buildText {
            text(this@toast.source(logLevel, color = Color.YELLOW))
        }.let { title ->
            runSafeGameConcurrent {
                mc.toastManager.add(logLevel.toast(title, message))
            }
        }
    }

    fun Any.logText(message: Text, logLevel: LogLevel = LogLevel.INFO) {
        runSafe {
            buildText {
                text(this@logText.source(logLevel))
                text(message)
            }
        }
    }

    fun Any.log(
        message: Text,
        logLevel: LogLevel = LogLevel.INFO,
        source: String = "",
        textSource: Text = Text.empty(),
    ) {
        buildText {
            text(this@log.source(logLevel, source, textSource))
            text(message)
        }.let { log ->
            runSafeGameConcurrent {
                player.sendMessage(log)
            }
        }
    }

    private fun Any.source(
        logLevel: LogLevel,
        source: String = "",
        textSource: Text = Text.empty(),
        color: Color = Color.GRAY,
    ) = buildText {
        text(logLevel.prefix())

        // ToDo: HUD elements

        when (this@source) {
            is LambdaCommand -> commandSource(this@source, color)
            is Module -> moduleSource(this@source, color)
            is Nameable -> {
                styled(color, italic = true) {
                    literal("${name.capitalize()} ")
                }
            }
        }

        if (source.isNotBlank()) {
            styled(color, italic = true) {
                literal("$source ")
            }
        }

        if (textSource.string.isNotBlank()) {
            text(textSource)
        }
    }

    private fun TextBuilder.commandSource(command: LambdaCommand, color: Color) {
        hoverEvent(HoverEvents.showText(buildText {
            literal(command.description)
            literal("\n")
            literal(command.usage)
            literal("\n")
            literal("Aliases: ")
            joinToText(command.aliases) {
                color(GuiSettings.primaryColor) {
                    literal(it)
                }
            }
        })) {
            styled(color, italic = true) {
                literal("${command.name.capitalize()} ")
            }
        }
    }

    private fun TextBuilder.moduleSource(module: Module, color: Color) {
        hoverEvent(HoverEvents.showText(buildText {
            literal(module.description)
            literal("\n")
            literal("Keybind: ")
            color(GuiSettings.primaryColor) {
                literal(module.keybind.keyCode.toString())
            }
            literal("\n")
            literal("Default tags: ")
            joinToText(module.defaultTags) {
                color(GuiSettings.primaryColor) {
                    literal(it.name)
                }
            }
            if (module.customTags.value.isNotEmpty()) {
                literal("\n")
                literal("Custom tags: ")
                joinToText(module.customTags.value) {
                    color(GuiSettings.primaryColor) {
                        literal(it.name)
                    }
                }
            }
        })) {
            styled(color, italic = true) {
                literal("${module.name.capitalize()} ")
            }
        }
    }

    private fun LogLevel.prefix() =
        buildText {
            hoverEvent(HoverEvents.showText(buildText {
                literal("Lambda ")
                color(logoColor) {
                    literal(Lambda.SYMBOL)
                }
                literal(" v${Lambda.VERSION}\n")
                literal("Runtime: ${Loader.runtime}\n")
                literal("Modules: ${ModuleRegistry.modules.size}\n")
                literal("Commands: ${CommandRegistry.commands.size}\n")
                literal(
                    "Settings: ${
                        Configuration.configurations.sumOf { config ->
                            config.configurables.sumOf { it.settings.size }
                        }
                    }"
                )
                literal("\n")
                literal("Synchronous listeners: ${EventFlow.syncListeners.size}\n")
                literal("Concurrent listeners: ${EventFlow.concurrentListeners.size}")

            })) {
                styled(logoColor) {
                    literal(Lambda.SYMBOL)
                }
                literal(" ")
            }

        }

    fun <T> TextBuilder.joinToText(
        elements: Collection<T>,
        separator: String = ", ",
        action: TextBuilder.(T) -> Unit,
    ) {
        elements.forEachIndexed { index, element ->
            if (index != 0) {
                literal(separator)
            }
            action(element)
        }
    }

    enum class LogLevel(
        val logoColor: Color,
        private val messageColor: Color,
        val type: SystemToast.Type,
    ) {
        DEBUG(Color.WHITE, Color.WHITE, SystemToast.Type.WORLD_BACKUP),
        INFO(Color.GREEN, Color.WHITE, SystemToast.Type.NARRATOR_TOGGLE),
        WARN(Color.YELLOW, Color.YELLOW, SystemToast.Type.WORLD_ACCESS_FAILURE),
        ERROR(Color.RED, Color.RED, SystemToast.Type.WORLD_ACCESS_FAILURE);

        fun toast(title: Text, message: Text): SystemToast =
            SystemToast.create(mc, type, title, message)

        fun text(message: String) = buildText {
            styled(messageColor) {
                literal(message)
            }
        }
    }
}
