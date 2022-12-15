package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.DelayedActivity
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.MessageSendHelper

class WaitAndSayActivity(private val message: String, delay: Long): DelayedActivity(delay) {
    override fun SafeClientEvent.onDelayedActivity() {
        MessageSendHelper.sendChatMessage(message)
    }

    override fun addExtraInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {
        textComponent.add("Message", primaryColor)
        textComponent.add(message, secondaryColor)
        textComponent.add("Delay", primaryColor)
        textComponent.add(delay.toString(), secondaryColor)

        if (activityStatus == ActivityStatus.RUNNING) {
            textComponent.add("Waited", primaryColor)
            textComponent.add((System.currentTimeMillis() - creationTime).toString(), secondaryColor)
        }
    }
}