package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import kotlin.random.Random

class FailingActivity : Activity() {
    override fun SafeClientEvent.onInitialize() {
        activityStatus = if (Random.nextBoolean()) {
            ActivityStatus.SUCCESS
        } else {
            ActivityStatus.FAILURE
        }
    }
}