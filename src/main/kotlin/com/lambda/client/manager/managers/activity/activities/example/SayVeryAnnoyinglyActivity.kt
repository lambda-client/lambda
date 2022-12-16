package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity

class SayVeryAnnoyinglyActivity(private val message: String): InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        message.forEach {
            subActivities.add(WaitAndSayActivity(it.toString(), 1000))
        }
    }
}