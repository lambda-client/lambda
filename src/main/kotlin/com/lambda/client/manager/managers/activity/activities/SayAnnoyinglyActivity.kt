package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.InstantActivity

class SayAnnoyinglyActivity(private val sayWhat: String): InstantActivity() {
    override fun SafeClientEvent.onInitialize() {
        sayWhat.split(" ").forEach {
            subActivities.add(WaitAndSayActivity(it, 1000))
        }
    }
}