package com.lambda.client.manager.managers.activity

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class WaitAndSayActivity(private val sayThis: String, private val waitUntil: Long): Activity() {
    override fun SafeClientEvent.initialize() {
        activityStatus = ActivityStatus.RUNNING
        LambdaEventBus.subscribe(this)
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (System.currentTimeMillis() > waitUntil) {
                MessageSendHelper.sendChatMessage(sayThis)
                activityStatus = ActivityStatus.SUCCESS
                LambdaEventBus.unsubscribe(this)
            }
        }
    }

}