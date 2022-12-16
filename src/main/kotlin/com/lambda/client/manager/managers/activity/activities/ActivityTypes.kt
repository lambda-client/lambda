package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent

interface TimeoutActivity {
    val timeout: Long
    var creationTime: Long
}

interface InstantActivity

interface DelayedActivity {
    val delay: Long
    var creationTime: Long

    fun SafeClientEvent.onDelayedActivity()
}

interface AttemptActivity {
    val maxAttempts: Int
    var usedAttempts: Int
}