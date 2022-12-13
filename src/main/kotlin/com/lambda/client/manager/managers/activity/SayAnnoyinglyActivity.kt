package com.lambda.client.manager.managers.activity

import com.lambda.client.event.SafeClientEvent

class SayAnnoyinglyActivity(private val sayWhat: String): Activity() {
    override fun SafeClientEvent.initialize() {
        sayWhat.split(" ").forEach {
            subActivities.add(WaitAndSayActivity(it, System.currentTimeMillis() + 1000))
        }
        activityStatus = ActivityStatus.RUNNING
    }
}