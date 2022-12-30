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
    var executeOnSuccess: (() -> Unit)? = null
    var executeOnFailure: ((Exception) -> Unit)? = null
    var executeOnFinalize: (() -> Unit)? = null
    val subActivities = ConcurrentLinkedDeque<Activity>()
    var activityStatus = ActivityStatus.UNINITIALIZED
    private var creationTime = 0L
    var owner: Activity = ActivityManager
    var depth = 0
    val name get() = this::class.simpleName
    val currentActivity: Activity get() = subActivities.peek()?.currentActivity ?: this

    enum class ActivityStatus {
        UNINITIALIZED,
        RUNNING,
        PENDING,
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
                if (!ListenerManager.listenerMap.containsKey(this@Activity)
                    && noSubActivities()
                    && this@Activity !is DelayedActivity
                ) finalize()
            }
            ActivityStatus.PENDING -> {
                owner.subActivities.remove(this@Activity)
                owner.subActivities.add(this@Activity)
            }
            ActivityStatus.SUCCESS -> {
                finalize()
            }
            ActivityStatus.FAILURE -> {
                finalize()
                LambdaMod.LOG.error("$name failed!")
            }
        }
    }

    fun SafeClientEvent.updateTypesOnTick() {
        if (this@Activity is TimeoutActivity) {
            if (System.currentTimeMillis() > creationTime + timeout) {
                if (this@Activity is AttemptActivity) {
                    if (usedAttempts >= maxAttempts) {
                        activityStatus = ActivityStatus.FAILURE
                        LambdaMod.LOG.error("$name fully timed out!")
                    } else {
                        usedAttempts++
                        initialize()
                        LambdaMod.LOG.warn("$name timed out!")
                    }
                } else {
                    activityStatus = ActivityStatus.FAILURE
                    LambdaMod.LOG.error("$name fully timed out!")
                }
            }
        }
        if (this@Activity is DelayedActivity) {
            if (System.currentTimeMillis() > creationTime + delay) {
                onDelayedActivity()
            }
        }
        if (this@Activity is AttemptActivity) {
            if (usedAttempts >= maxAttempts) {
                activityStatus = ActivityStatus.FAILURE
                LambdaMod.LOG.error("$name failed after $maxAttempts attempts!")
            }
        }
        if (this@Activity is RotatingActivity) {
            sendPlayerPacket {
                rotate(rotation)
            }
        }
    }

    private fun SafeClientEvent.initialize() {
        activityStatus = ActivityStatus.RUNNING
        creationTime = System.currentTimeMillis()
        onInitialize()
        if (this@Activity is RotatingActivity) {
            sendPlayerPacket {
                rotate(rotation)
            }
        }
        LambdaMod.LOG.info("${System.currentTimeMillis()} Initialized $name ${System.currentTimeMillis() - ActivityManager.lastActivity.creationTime}ms after last activity creation")
    }

    open fun SafeClientEvent.onInitialize() {}

    private fun SafeClientEvent.finalize() {
        onFinalize()
        executeOnFinalize?.invoke()
        owner.subActivities.remove(this@Activity)

        if (this@Activity is LoopingAmountActivity) {
            if (currentLoops++ < maxLoops || maxLoops == 0) {
                activityStatus = ActivityStatus.UNINITIALIZED
                owner.subActivities.add(this@Activity)
                LambdaMod.LOG.info("Looping $name [$currentLoops/${if (maxLoops == 0) "∞" else maxLoops}] ")
            }
        }

        if (this@Activity is LoopingUntilActivity) {
            if (!loopUntil()) {
                currentLoops++
                activityStatus = ActivityStatus.UNINITIALIZED
                owner.subActivities.add(this@Activity)
                LambdaMod.LOG.info("Looping $name ($currentLoops) ")
            }
        }

        with(ActivityManager) {
            updateCurrentActivity()
        }

        //        LambdaMod.LOG.info("${System.currentTimeMillis()} Finalized $name after ${System.currentTimeMillis() - creationTime}ms")
        MessageSendHelper.sendRawChatMessage("$name took ${System.currentTimeMillis() - creationTime}ms")
    }

    open fun SafeClientEvent.onFinalize() {}

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

//        LambdaMod.LOG.info("${System.currentTimeMillis()} Added ${activities.size} sub activities to $name")
    }

    fun Activity.addSubActivities(vararg activities: Activity) {
        addSubActivities(activities.toList())
    }

    fun getAllSubActivities(): MutableList<Activity> {
        val activities = mutableListOf<Activity>()

        if (this !is ActivityManager) {
            activities.add(this)
        }

        activities.addAll(subActivities.flatMap { it.getAllSubActivities() })

        return activities
    }

    fun noSubActivities() = subActivities.isEmpty()

    fun SafeClientEvent.onSuccess() {
        finalize()
    }

    fun SafeClientEvent.onFailure() {
        finalize()

        LambdaMod.LOG.warn("$name failed!")
    }

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