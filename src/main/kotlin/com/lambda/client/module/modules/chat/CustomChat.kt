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
    private val textMode by setting("Message", TextMode.JAPANESE)
    private val decoMode by setting("Separator", DecoMode.NONE)
    private val commands by setting("Commands", false)
    private val spammer by setting("Spammer", false)
    private val customText by setting("Custom Text", "Default")

    private enum class DecoMode {
        SEPARATOR, CLASSIC, NONE
    }

    private enum class TextMode {
        NAME, ON_TOP, WEBSITE, JAPANESE, CUSTOM
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
            modifier.enable()
        }

        onDisable {
            modifier.disable()
        }
    }

    private fun getText() = when (textMode) {
        TextMode.NAME -> "ᴋᴀᴍɪ ʙʟᴜᴇ"
        TextMode.ON_TOP -> "ᴋᴀᴍɪ ʙʟᴜᴇ ᴏɴ ᴛᴏᴘ"
        TextMode.WEBSITE -> "ｋａｍｉｂｌｕｅ．ｏｒｇ"
        TextMode.JAPANESE -> "上にカミブルー"
        TextMode.CUSTOM -> customText
    }

    private fun getFull() = when (decoMode) {
        DecoMode.NONE -> " " + getText()
        DecoMode.CLASSIC -> " \u00ab " + getText() + " \u00bb"
        DecoMode.SEPARATOR -> " | " + getText()
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (timer.tick(5L) && textMode == TextMode.CUSTOM && customText.equals("Default", ignoreCase = true)) {
                MessageSendHelper.sendWarningMessage("$chatName Warning: In order to use the custom $name, please change the CustomText setting in ClickGUI")
            }
        }
    }


}
