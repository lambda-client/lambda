package com.lambda.client.manager.managers.activities

import com.lambda.client.event.SafeClientEvent

class SayAnnoyinglyActivity(private val sayWhat: String): Activity() {
    override fun SafeClientEvent.initialize(): ActivityStatus {
        sayWhat.split(" ").forEach {
            subActivities.add(WaitAndSayActivity(it, System.currentTimeMillis() + 1000))
        }
        return ActivityStatus.RUNNING
    }

    override fun SafeClientEvent.doTick(): ActivityStatus {
        return ActivityStatus.SUCCESS
    }

}