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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.command.LambdaCommand
import com.lambda.config.ConfigLoader
import com.lambda.core.Loader
import com.lambda.event.EventFlow
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.text.HoverEvents
import com.lambda.util.text.TextBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.hoverEvent
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import com.lambda.util.text.text
import net.minecraft.client.toast.SystemToast
import net.minecraft.text.Text
import java.awt.Color
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object CommunicationUtils {
    val ascii = """

        ⣰⡛⠶⣄⠀⠀⠀⠀⠀⠀
        ⠑⠭⣛⡜⣳⡀⠀⠀⠀⠀
        ⠀⠀⠹⣾⣥⣛⡄⠀⠀⠀
        ⠀⠀⢠⣿⢯⣷⣻⡄⠀⠀
        ⠀⢠⣿⣿⣿⢶⣏⡿⡄⠀
        ⢠⣿⣿⡿⠃⠘⣿⣼⣻⣄
        ⠻⢿⡿⠁⠀⠀⠘⢷⡽⠞

    """.trimIndent()

    fun currentTime(): String = LocalDateTime.now()
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG))

    fun Any.debug(message: String, source: String = "") = log(LogLevel.Debug.text(message), LogLevel.Debug, source)
    fun Any.debug(message: Text, source: Text = Text.empty()) = log(message, LogLevel.Debug, textSource = source)
    fun Any.info(message: String, source: String = "") = log(LogLevel.Info.text(message), LogLevel.Info, source)
    fun Any.info(message: Text, source: Text = Text.empty()) = log(message, LogLevel.Info, textSource = source)
    fun Any.warn(message: String, source: String = "") = log(LogLevel.Warn.text(message), LogLevel.Warn, source)
    fun Any.warn(message: Text, source: Text = Text.empty()) = log(message, LogLevel.Warn, textSource = source)
    fun Any.logError(message: String, source: String = "") = log(LogLevel.Error.text(message), LogLevel.Error, source)
    fun Any.logError(message: Text, source: Text = Text.empty()) = log(message, LogLevel.Error, textSource = source)
    fun Any.logError(message: String, throwable: Throwable) = logError(message, throwable.message ?: "")

    fun Any.toast(message: String, logLevel: LogLevel = LogLevel.Info) {
        toast(logLevel.text(message), logLevel)
    }

    fun Any.toast(message: Text, logLevel: LogLevel = LogLevel.Info) {
        buildText {
            text(this@toast.source(logLevel, color = Color.YELLOW))
        }.let { title ->
            runSafeGameScheduled {
                mc.toastManager.add(logLevel.toast(title, message))
            }
        }
    }

    fun Any.logText(message: Text, logLevel: LogLevel = LogLevel.Info) {
        runSafe {
            buildText {
                text(this@logText.source(logLevel))
                text(message)
            }
        }
    }

    fun Any.log(
        message: Text,
        logLevel: LogLevel = LogLevel.Info,
        source: String = "",
        textSource: Text = Text.empty(),
        inGameOverlay: Boolean = false
    ) {
        buildText {
            text(this@log.source(logLevel, source, textSource))
            text(message)
        }.let { log ->
            runSafeGameScheduled {
                player.sendMessage(log, inGameOverlay)
            }
        }
    }

    private fun Any.source(
        logLevel: LogLevel,
        source: String = "",
        textSource: Text = Text.empty(),
        color: Color = Color.GRAY,
    ) = buildText {
        text(prefix(logLevel.logoColor))

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
            if (command.description.isNotBlank()) {
                literal(command.description)
            }
            if (command.usage.isNotBlank()) {
                literal("\n")
                literal("Usage: ")
                color(ClickGuiLayout.primaryColor) {
                    literal(command.usage)
                }
            }
            if (command.aliases.isNotEmpty()) {
                literal("\n")
                literal("Aliases: ")
                joinToText(command.aliases) {
                    color(ClickGuiLayout.primaryColor) {
                        literal(it)
                    }
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
            if (module.description.isNotBlank()) {
                literal(module.description)
                literal("\n")
            }
            literal("Keybind: ")
            color(ClickGuiLayout.primaryColor) {
                if (module.keybind.key != 0 || module.keybind.mouse > -1) {
                    literal(module.keybind.name)
                } else {
                    literal("Unbound")
                }

            }
        })) {
            styled(color, italic = true) {
                literal("${module.name.capitalize()} ")
            }
        }
    }

    fun prefix(color: Color) =
        buildText {
            hoverEvent(HoverEvents.showText(buildText {
                literal("Lambda ")
                color(color) {
                    literal(Lambda.Symbol)
                }
                literal(" v${Lambda.Version}\n")
                literal("Runtime: ${Loader.runtime}\n")
                literal("Modules: ${ModuleRegistry.modules.size}\n")
                literal("Commands: ${CommandRegistry.commands.size}\n")
                literal(
                    "Settings: ${
                        ConfigLoader.configCategories.sumOf { config ->
                            config.configs.sumOf {
                                var count = 0
                                it.forEachSetting { _, _ -> count++ }
                                count
                            }
                        }
                    }"
                )
                literal("\n")
                literal("Synchronous listeners: ${EventFlow.syncListeners.size}\n")
                literal("Concurrent listeners: ${EventFlow.concurrentListeners.size}")

            })) {
                styled(color) {
                    literal(Lambda.Symbol)
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
        Debug(Color.WHITE, Color.WHITE, SystemToast.Type.WORLD_BACKUP),
        Info(Color.GREEN, Color.WHITE, SystemToast.Type.NARRATOR_TOGGLE),
        Warn(Color.YELLOW, Color.YELLOW, SystemToast.Type.WORLD_ACCESS_FAILURE),
        Error(Color.RED, Color.RED, SystemToast.Type.WORLD_ACCESS_FAILURE);

        fun toast(title: Text, message: Text): SystemToast =
            SystemToast.create(mc, type, title, message)

        fun text(message: String) = buildText {
            styled(messageColor) {
                literal(message)
            }
        }
    }
}
