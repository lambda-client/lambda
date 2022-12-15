package com.lambda.client.manager.managers.activity.types

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

abstract class DelayedActivity(val delay: Long) : Activity() {
    var creationTime = 0L

    override fun SafeClientEvent.onInitialize() {
        creationTime = System.currentTimeMillis()
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (System.currentTimeMillis() > creationTime + delay) {
                onDelayedActivity()
                activityStatus = ActivityStatus.SUCCESS
            }
        }
    }

    abstract fun SafeClientEvent.onDelayedActivity()
}