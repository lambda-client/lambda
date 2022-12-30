package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent

class SayVeryAnnoyingly(private val message: String) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        message.forEach {
            addSubActivities(ListenAndWait(it.toString(), 250))
        }
    }
}