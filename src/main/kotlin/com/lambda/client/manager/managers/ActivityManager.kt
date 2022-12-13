package com.lambda.client.manager.managers

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.Manager
import com.lambda.client.manager.managers.activities.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (subActivities.isEmpty() || it.phase != TickEvent.Phase.START) return@safeListener

            onTick()
        }
    }

    override fun SafeClientEvent.initialize(): ActivityStatus {
        return ActivityStatus.RUNNING
    }

    override fun SafeClientEvent.doTick(): ActivityStatus {
        return ActivityStatus.RUNNING
    }

    fun addActivity(activity: Activity) {
        subActivities.add(activity)
    }
}