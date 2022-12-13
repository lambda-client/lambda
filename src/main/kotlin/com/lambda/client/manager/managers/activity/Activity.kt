package com.lambda.client.manager.managers.activity

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import java.util.concurrent.ConcurrentLinkedDeque

abstract class Activity {
    val subActivities = ConcurrentLinkedDeque<Activity>()
    var activityStatus = ActivityStatus.UNINITIALIZED

    enum class ActivityStatus {
        UNINITIALIZED,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    fun SafeClientEvent.updateActivities() {
        when (activityStatus) {
            ActivityStatus.UNINITIALIZED -> {
                initialize()
            }
            ActivityStatus.RUNNING -> {
                subActivities.peek()?.let {
                    with(it) {
                        if (it.activityStatus == ActivityStatus.SUCCESS) {
                            subActivities.pop()
//                            LambdaEventBus.unsubscribe(it)
                        } else {
                            updateActivities()
                        }
                    }
                } ?: run {
//                    LambdaEventBus.subscribe(this)
                }
            }
            ActivityStatus.SUCCESS -> {
                // do nothing
            }
            ActivityStatus.FAILURE -> {
//                activityStatus = ActivityStatus.UNINITIALIZED
//                subActivities.clear()
            }
        }
    }

    abstract fun SafeClientEvent.initialize()

    fun currentActivity(): Activity {
        return subActivities.peek() ?: this
    }

    override fun toString(): String {
        return "Name: ${javaClass.simpleName} State: $activityStatus SubActivities: $subActivities"
    }
}