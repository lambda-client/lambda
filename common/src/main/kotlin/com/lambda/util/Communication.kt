package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.threading.runOnGameThread
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeOnGameThread
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

    fun Any.toast(message: String, logLevel: LogLevel = LogLevel.INFO) {
        toast(logLevel.text(message), logLevel)
    }

    fun Any.toast(message: Text, logLevel: LogLevel = LogLevel.INFO) {
        buildText {
            text(this@toast.source(logLevel, color = Color.YELLOW))
        }.let { title ->
            runSafeOnGameThread {
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

    fun Any.log(message: Text, logLevel: LogLevel = LogLevel.INFO, source: String = "", textSource: Text = Text.empty()) {
        buildText {
            text(this@log.source(logLevel, source, textSource))
            text(message)
        }.let { log ->
            runSafeOnGameThread {
                player.sendMessage(log)
            }
        }
    }

    private fun Any.source(
        logLevel: LogLevel,
        source: String = "",
        textSource: Text = Text.empty(),
        color: Color = Color.GRAY
    ) = buildText {
        text(logLevel.prefix())

//        if (this@source is LambdaCommand) {
//            styled(color, italic = true) {
//                literal("Command ")
//            }
//        }
//
//        if (this@source is Module) {
//            styled(color, italic = true) {
//                literal("Module ")
//            }
//        }
//
//        // ToDo: HUD elements
//
        if (this@source is Nameable) {
            styled(color, italic = true) {
                literal("${name.capitalize()} ")
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

    private fun LogLevel.prefix() =
        buildText {
            styled(logoColor) {
                literal(Lambda.SYMBOL)
            }
            literal(" ")
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
