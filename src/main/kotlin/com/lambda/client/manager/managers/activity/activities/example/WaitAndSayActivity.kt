package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.DelayedActivity
import com.lambda.client.util.text.MessageSendHelper

class WaitAndSayActivity(
    private val message: String,
    override val delay: Long = 1000L,
    override var creationTime: Long = 0L
): DelayedActivity, Activity() {
    override fun SafeClientEvent.onDelayedActivity() {
        MessageSendHelper.sendChatMessage(message)
    }
}