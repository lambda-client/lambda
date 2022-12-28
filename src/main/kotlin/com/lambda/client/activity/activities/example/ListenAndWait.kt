package com.lambda.client.activity.activities.example

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.TimeoutActivity
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class ListenAndWait(
    private val message: String,
    override val timeout: Long
) : TimeoutActivity, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            MessageSendHelper.sendChatMessage(message)
        }
    }
}