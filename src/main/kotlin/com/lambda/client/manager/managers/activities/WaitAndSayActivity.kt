package com.lambda.client.manager.managers.activities

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper

class WaitAndSayActivity(private val sayThis: String, private val waitUntil: Long): Activity() {
    override fun SafeClientEvent.initialize(): ActivityStatus {
        return ActivityStatus.RUNNING
    }

    override fun SafeClientEvent.doTick(): ActivityStatus {
        return if (System.currentTimeMillis() > waitUntil) {
            MessageSendHelper.sendChatMessage(sayThis)
            ActivityStatus.SUCCESS
        } else {
            ActivityStatus.RUNNING
        }
    }

}