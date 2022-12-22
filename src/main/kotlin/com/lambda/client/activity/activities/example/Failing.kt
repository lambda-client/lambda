package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import kotlin.random.Random

class Failing : Activity() {
    override fun SafeClientEvent.onInitialize() {
        activityStatus = if (Random.nextBoolean()) {
            ActivityStatus.SUCCESS
        } else {
            ActivityStatus.FAILURE
        }
    }
}