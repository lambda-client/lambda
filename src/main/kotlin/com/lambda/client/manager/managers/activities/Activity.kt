package com.lambda.client.manager.managers.activities

import com.lambda.client.event.SafeClientEvent
import java.util.concurrent.ConcurrentLinkedDeque

abstract class Activity {
    val subActivities = ConcurrentLinkedDeque<Activity>()
    private var activityStatus = ActivityStatus.UNINITIALIZED

    enum class ActivityStatus {
        UNINITIALIZED,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    fun SafeClientEvent.onTick() {
        when (activityStatus) {
            ActivityStatus.UNINITIALIZED -> {
                activityStatus = initialize()
            }
            ActivityStatus.RUNNING -> {
                if (subActivities.peek()?.activityStatus == ActivityStatus.SUCCESS) {
                    subActivities.pop()
                }
                if (subActivities.isEmpty()) {
                    activityStatus = doTick()
                } else {
                    with(subActivities.peek()) {
                        onTick()
                    }
                }
            }
            ActivityStatus.SUCCESS -> {
//                activityStatus = ActivityStatus.UNINITIALIZED
//                subActivities.clear()
            }
            ActivityStatus.FAILURE -> {
                activityStatus = ActivityStatus.UNINITIALIZED
                subActivities.clear()
            }
        }
    }

    abstract fun SafeClientEvent.initialize(): ActivityStatus

    abstract fun SafeClientEvent.doTick(): ActivityStatus

    private fun currentActivity(): Activity {
        return subActivities.peek() ?: this
    }

    override fun toString(): String {
        return "Current activity: ${currentActivity()::class.simpleName}"
    }
}