package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent

class SayAnnoyinglyActivity(private val message: String): InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        message.split(" ").forEach {
            subActivities.add(SayVeryAnnoyinglyActivity(it))
        }
    }
}