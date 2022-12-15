package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent

class SayVeryAnnoyinglyActivity(private val message: String): InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        message.forEach {
            subActivities.add(WaitAndSayActivity(it.toString(), 1000))
        }
    }

    override fun addExtraInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {
        textComponent.add("Message", primaryColor)
        textComponent.add(message, secondaryColor)
    }
}