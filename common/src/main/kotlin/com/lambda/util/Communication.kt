package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.command.LambdaCommand
import com.lambda.module.Module
import com.lambda.threading.runSafe
import com.lambda.util.text.*
import net.minecraft.client.toast.SystemToast
import net.minecraft.text.Text

object Communication {
    fun Any.debug(message: String) = log(LogLevel.DEBUG.text(message), LogLevel.DEBUG)
    fun Any.debug(message: Text) = log(message, LogLevel.DEBUG)
    fun Any.info(message: String) = log(LogLevel.INFO.text(message), LogLevel.INFO)
    fun Any.info(message: Text) = log(message, LogLevel.INFO)
    fun Any.warn(message: String) = log(LogLevel.WARN.text(message), LogLevel.WARN)
    fun Any.warn(message: Text) = log(message, LogLevel.WARN)
    fun Any.logError(message: String) = logError(LogLevel.ERROR.text(message))
    fun Any.logError(message: Text) = log(message, LogLevel.ERROR)

    fun Any.toast(message: String, logLevel: LogLevel = LogLevel.INFO) {
        toast(logLevel.text(message), logLevel)
    }

    fun Any.toast(message: Text, logLevel: LogLevel = LogLevel.INFO) {
        runSafe {
            buildText {
                text(this@toast.source(logLevel, Color.YELLOW))
            }.let { title ->
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

    fun Any.log(message: Text, logLevel: LogLevel = LogLevel.INFO) {
        runSafe {
            buildText {
                text(this@log.source(logLevel))
                text(message)
            }.let { log ->
                player.sendMessage(log)
            }
        }
    }

    private fun Any.source(logLevel: LogLevel, color: Color = Color.GREY) = buildText {
        text(logLevel.prefix())

        if (this@source is LambdaCommand) {
            styled(color, italic = true) {
                literal("Command ")
            }
        }

        if (this@source is Module) {
            styled(color, italic = true) {
                literal("Module ")
            }
        }

        // ToDo: HUD elements

        if (this@source is Nameable) {
            styled(color, italic = true, underlined = true) {
                literal(name.replaceFirstChar(Char::titlecase))
            }
        }

        styled(color, italic = true) {
            literal(" \$ ")
        }
    }

    private fun LogLevel.prefix() =
        buildText {
            literal(" ")
            styled(logoColor) {
                literal(Lambda.SYMBOL)
            }
            literal(" ")
        }

    enum class LogLevel(val logoColor: Color, val messageColor: Color, val type: SystemToast.Type) {
        DEBUG(Color.WHITE, Color.WHITE, SystemToast.Type.WORLD_BACKUP),
        INFO(Color.GREEN, Color.WHITE, SystemToast.Type.NARRATOR_TOGGLE),
        WARN(Color.YELLOW, Color.YELLOW, SystemToast.Type.WORLD_ACCESS_FAILURE),
        ERROR(Color.RED, Color.YELLOW, SystemToast.Type.WORLD_ACCESS_FAILURE);

        fun toast(title: Text, message: Text): SystemToast =
            SystemToast.create(mc, type, title, message)

        fun text(message: String) = buildText {
            styled(messageColor) {
                literal(message)
            }
        }
    }
}
