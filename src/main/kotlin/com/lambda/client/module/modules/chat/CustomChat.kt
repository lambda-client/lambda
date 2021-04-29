package com.lambda.client.module.modules.chat

import com.lambda.client.manager.managers.MessageManager.newMessageModifier
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageDetection
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.min

internal object CustomChat : Module(
    name = "CustomChat",
    category = Category.CHAT,
    description = "Add a custom ending to your message!",
    showOnArray = false,
    modulePriority = 200
) {
    private val decoMode by setting("Separator", DecoMode.NONE)
    private val commands by setting("Commands", false)
    private val spammer by setting("Spammer", false)
    private val customText by setting("Custom Text", "Default")

    private enum class DecoMode {
        SEPARATOR, CLASSIC, NONE
    }

    private val timer = TickTimer(TimeUnit.SECONDS)
    private val modifier = newMessageModifier(
        filter = {
            (commands || MessageDetection.Command.ANY detectNot it.packet.message)
                && (spammer || it.source !is Spammer)
        },
        modifier = {
            val message = it.packet.message + getFull()
            message.substring(0, min(256, message.length))
        }
    )

    init {
        onEnable {
            if (customText.equals("Default", ignoreCase = true)) {
                MessageSendHelper.sendWarningMessage("$chatName In order to use CustomChat, please change the CustomText setting in ClickGUI")
                disable()
            } else {
                modifier.enable()
            }
        }

        onDisable {
            modifier.disable()
        }
    }

    private fun getText() = customText

    private fun getFull() = when (decoMode) {
        DecoMode.NONE -> " " + getText()
        DecoMode.CLASSIC -> " \u00ab " + getText() + " \u00bb"
        DecoMode.SEPARATOR -> " | " + getText()
    }
}
