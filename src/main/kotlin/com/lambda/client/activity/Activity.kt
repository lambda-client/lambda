package com.lambda.client.activity

import com.lambda.client.LambdaMod
import com.lambda.client.activity.activities.*
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.MAX_DEPTH
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import java.util.concurrent.ConcurrentLinkedDeque

abstract class Activity {
    val subActivities = ConcurrentLinkedDeque<Activity>()
    var activityStatus = ActivityStatus.UNINITIALIZED
    var owner: Activity = ActivityManager
    var depth = 0

    enum class ActivityStatus {
        UNINITIALIZED,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    fun SafeClientEvent.updateActivity() {
        when (activityStatus) {
            ActivityStatus.UNINITIALIZED -> {
                initialize()
                updateActivity()
            }
            ActivityStatus.RUNNING -> {
                if (this@Activity is TimeoutActivity) {
                    if (System.currentTimeMillis() > creationTime + timeout) {
                        if (this@Activity is AttemptActivity) {
                            if (usedAttempts >= maxAttempts) {
                                activityStatus = ActivityStatus.FAILURE
                                LambdaMod.LOG.error("${this@Activity::class.simpleName} fully timed out!")
                            } else {
                                usedAttempts++
                                initialize()
                                LambdaMod.LOG.error("${this@Activity::class.simpleName} timed out!")
                            }
                        } else {
                            activityStatus = ActivityStatus.FAILURE
                            LambdaMod.LOG.error("${this@Activity::class.simpleName} fully timed out!")
                        }
                    }
                }
                if (this@Activity is InstantActivity) {
                    activityStatus = ActivityStatus.SUCCESS
                }
                if (this@Activity is DelayedActivity) {
                    if (System.currentTimeMillis() > creationTime + delay) {
                        onDelayedActivity()
                    }
                }
                if (this@Activity is AttemptActivity) {
                    if (usedAttempts >= maxAttempts) {
                        activityStatus = ActivityStatus.FAILURE
                        LambdaMod.LOG.error("${this@Activity::class.simpleName} failed after $maxAttempts attempts!")
                    }
                }
                if (this@Activity is RotatingActivity) {
                    sendPlayerPacket {
                        rotate(rotation)
                    }
                }
            }
            ActivityStatus.SUCCESS -> {
                finalize()
//                LambdaMod.LOG.info("${this@Activity} activity finished successfully!")
            }
            ActivityStatus.FAILURE -> {
                finalize()
                LambdaMod.LOG.error("Activity ${this@Activity::class.simpleName} failed!")
            }
        }
    }

    private fun SafeClientEvent.initialize() {
        activityStatus = ActivityStatus.RUNNING
        if (this@Activity is TimeoutActivity) {
            creationTime = System.currentTimeMillis()
        }
        if (this@Activity is DelayedActivity) {
            creationTime = System.currentTimeMillis()
        }
        onInitialize()
        if (this@Activity is RotatingActivity) {
            sendPlayerPacket {
                rotate(rotation)
            }
        }
//        LambdaMod.LOG.info("Initialized activity: ${this@Activity}")
    }

    open fun SafeClientEvent.onInitialize() {}

    private fun SafeClientEvent.finalize() {
        onFinalize()
        owner.subActivities.remove(this@Activity)
//        LambdaMod.LOG.info("Finalized activity: ${this@Activity}")

        if (this@Activity is InstantActivity) {
            with(ActivityManager) {
                runActivity()
            }
        }
    }

    open fun SafeClientEvent.onFinalize() {}
    fun currentActivity(): Activity = subActivities.peek()?.currentActivity() ?: this

    fun Activity.addSubActivities(activities: List<Activity>) {
        if (activities.isEmpty()) return

        if (depth > MAX_DEPTH) {
            MessageSendHelper.sendErrorMessage("Activity depth exceeded $MAX_DEPTH!")
            ActivityManager.reset()
            return
        }

        activities.forEach {
            it.owner = this
            it.depth = depth + 1
        }
        subActivities.addAll(activities)

//        LambdaMod.LOG.info("Added ${activities.size} sub activities to ${this::class.simpleName}")
    }

    fun Activity.addSubActivities(vararg activities: Activity) {
        addSubActivities(activities.toList())
    }

    fun noSubActivities() = subActivities.isEmpty()

    override fun toString(): String {
        return "Name: ${javaClass.simpleName} State: $activityStatus SubActivities: $subActivities"
    }

    open fun addExtraInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {}

    fun appendInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {
        if (this !is ActivityManager) {
            ListenerManager.listenerMap[this@Activity]?.let {
                textComponent.add("SYNC", secondaryColor)
            }
            ListenerManager.asyncListenerMap[this@Activity]?.let {
                textComponent.add("ASYNC", secondaryColor)
            }
            textComponent.add("Name", primaryColor)
            textComponent.add("${javaClass.simpleName} ", secondaryColor)
            textComponent.add("State", primaryColor)
            textComponent.add(activityStatus.name, secondaryColor)
            this::class.java.declaredFields.forEach { field ->
                field.isAccessible = true
                val name = field.name
                val value = field.get(this)
                textComponent.add(name.capitalize(), primaryColor)
                textComponent.add(value.toString(), secondaryColor)
            }
            textComponent.addLine("")
        }
        addExtraInfo(textComponent, primaryColor, secondaryColor)
        subActivities.forEach {
            repeat(depth) {
                textComponent.add("   ")
            }
            it.appendInfo(textComponent, primaryColor, secondaryColor)
        }
    }
}