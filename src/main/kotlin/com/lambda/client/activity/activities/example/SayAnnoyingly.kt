package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class SayAnnoyingly(private val message: String) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        message.split(" ").forEach {
            addSubActivities(SayVeryAnnoyingly(it))
        }
    }
}