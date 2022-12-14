package com.lambda.client.manager.managers.activity

import com.lambda.client.LambdaMod
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
                            finalize(this@Activity)
                        } else {
                            updateActivities()
                        }
                    }
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

    private fun SafeClientEvent.initialize() {
        onInitialize()
        activityStatus = ActivityStatus.RUNNING
        LambdaEventBus.subscribe(this@Activity)
        LambdaMod.LOG.info("Initialized activity: ${this@Activity}")
    }

    open fun SafeClientEvent.onInitialize() {}

    private fun SafeClientEvent.finalize(owner: Activity) {
        onFinalize()
        owner.subActivities.pop()
        LambdaEventBus.unsubscribe(this@Activity)
        LambdaMod.LOG.info("Finalized activity: ${this@Activity}")
    }

    open fun SafeClientEvent.onFinalize() {}

    fun currentActivity(): Activity {
        return subActivities.peek() ?: this
    }

    override fun toString(): String {
        return "Name: ${javaClass.simpleName} State: $activityStatus SubActivities: $subActivities"
    }
}