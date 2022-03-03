package com.lambda.client.module.modules.chat

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.manager.managers.MessageManager.newMessageModifier
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageDetection
import kotlin.math.min

object FancyChat : Module(
    name = "FancyChat",
    description = "Makes messages you send fancy",
    category = Category.CHAT,
    showOnArray = false,
    modulePriority = 100
) {
    private val uwu by setting("uwu", true)
    private val leet by setting("1337", false)
    private val mock by setting("mOcK", false)
    private val green by setting(">", false)
    private val blue by setting("`", false)
    private val randomSetting by setting("Random Case", true, { mock })
    private val commands by setting("Commands", false)
    private val spammer by setting("Spammer", false)

    private val modifier = newMessageModifier(
        filter = {
            (commands || MessageDetection.Command.ANY detectNot it.packet.message)
                && (spammer || it.source !is Spammer)
        },
        modifier = {
            val message = getText(it.packet.message)
            message.substring(0, min(256, message.length))
        }
    )

    init {
        onEnable {
            modifier.enable()
        }

        onDisable {
            modifier.disable()
        }
    }

    private fun getText(s: String): String {
        var string = s
        if (uwu) string = uwuConverter(string)
        if (leet) string = leetConverter(string)
        if (mock) string = mockingConverter(string)
        if (green) string = greenConverter(string)
        if (blue) string = blueConverter(string)
        return string
    }

    private fun greenConverter(input: String): String {
        return "> $input"
    }

    private fun blueConverter(input: String): String {
        return "`$input"
    }

    override fun getHudInfo(): String {
        val returned = StringBuilder()
        if (uwu) returned.append("uwu")
        if (leet) returned.append(" 1337")
        if (mock) returned.append(" mOcK")
        if (green) returned.append(" >")
        if (blue) returned.append(" `")
        return returned.toString()
    }

    private fun leetConverter(input: String): String {
        val message = StringBuilder()
        for (element in input) {
            var inputChar = element.toString() + ""
            inputChar = inputChar.lowercase()
            inputChar = leetSwitch(inputChar)
            message.append(inputChar)
        }
        return message.toString()
    }

    private fun mockingConverter(input: String): String {
        val message = StringBuilder()
        for (i in input.indices) {
            var inputChar = input[i].toString() + ""
            val rand = if (randomSetting) (0..1).random() else 0
            inputChar = if (!MathUtils.isNumberEven(i + rand)) inputChar.uppercase() else inputChar.lowercase()
            message.append(inputChar)
        }
        return message.toString()
    }

    private fun uwuConverter(input: String): String {
        var lInput = input
        lInput = lInput.replace("ove", "uv")
        lInput = lInput.replace("the", "da")
        lInput = lInput.replace("is", "ish")
        lInput = lInput.replace("r", "w")
        lInput = lInput.replace("ve", "v")
        lInput = lInput.replace("l", "w")
        return lInput
    }

    private fun leetSwitch(i: String): String {
        return when (i) {
            "a" -> "4"
            "e" -> "3"
            "g" -> "6"
            "l", "i" -> "1"
            "o" -> "0"
            "s" -> "$"
            "t" -> "7"
            else -> i
        }
    }
}
