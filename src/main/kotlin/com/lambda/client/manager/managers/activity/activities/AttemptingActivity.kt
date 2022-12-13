package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity

class AttemptingActivity(private val attempts: Int) : Activity() {
    override fun SafeClientEvent.initialize() {}
}