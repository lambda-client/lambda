package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.DelayedActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper

class WaitAndSay(
    private val message: String,
    override val delay: Long = 1000L
) : DelayedActivity, Activity() {
    override fun SafeClientEvent.onDelayedActivity() {
        MessageSendHelper.sendChatMessage(message)
        activityStatus = ActivityStatus.SUCCESS
    }
}