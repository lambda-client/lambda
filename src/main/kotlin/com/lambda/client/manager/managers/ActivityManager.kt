package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.Manager
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (subActivities.isEmpty() || it.phase != TickEvent.Phase.START) return@safeListener

            updateActivities()

            LambdaMod.LOG.error(currentActivity().toString())
        }
    }

    override fun SafeClientEvent.initialize() {
        activityStatus = ActivityStatus.RUNNING
    }

    fun addActivity(activity: Activity) {
        subActivities.add(activity)
    }
}