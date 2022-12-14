package com.lambda.client.manager.managers.activity.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.types.DelayedActivity
import com.lambda.client.util.text.MessageSendHelper

class WaitAndSayActivity(private val sayThis: String, delay: Long): DelayedActivity(delay) {
    override fun SafeClientEvent.onDelayedActivity() {
        MessageSendHelper.sendChatMessage(sayThis)
    }
}