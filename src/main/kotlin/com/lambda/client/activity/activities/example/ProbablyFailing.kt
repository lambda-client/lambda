package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import java.lang.Exception
import kotlin.random.Random

class ProbablyFailing : Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (Random.nextBoolean()) {
            success()
        } else {
            failedWith(Exception("Randomly failed"))
        }
    }
}