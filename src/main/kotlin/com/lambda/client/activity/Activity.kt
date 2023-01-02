package com.lambda.client.activity

import com.lambda.client.activity.activities.types.AttemptActivity.Companion.checkAttempt
import com.lambda.client.activity.activities.types.DelayedActivity
import com.lambda.client.activity.activities.types.DelayedActivity.Companion.checkDelayed
import com.lambda.client.activity.activities.types.LoopingAmountActivity.Companion.checkLoopingAmount
import com.lambda.client.activity.activities.types.LoopingUntilActivity.Companion.checkLoopingUntil
import com.lambda.client.activity.activities.types.RotatingActivity.Companion.checkRotating
import com.lambda.client.activity.activities.types.TimeoutActivity.Companion.checkTimeout
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.misc.ActivityManagerHud
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.MAX_DEPTH
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentLinkedDeque

abstract class Activity {
    val subActivities = ConcurrentLinkedDeque<Activity>()
    var activityStatus = ActivityStatus.UNINITIALIZED
    private var creationTime = 0L
    var owner: Activity = ActivityManager
    var depth = 0
    val name get() = this::class.simpleName
    val age get() = if (creationTime != 0L) System.currentTimeMillis() - creationTime else 0L
    val currentActivity: Activity get() = subActivities.peek()?.currentActivity ?: this

    var executeOnInitialize: (SafeClientEvent.() -> Unit)? = null
    var executeOnSuccess: (SafeClientEvent.() -> Unit)? = null
    var executeOnFailure: ((Exception) -> Boolean)? = null

    open fun SafeClientEvent.onInitialize() {}

    open fun SafeClientEvent.onSuccess() {}

    /* Return true to catch the exception */
    open fun SafeClientEvent.onFailure(exception: Exception): Boolean = false

    open fun addExtraInfo(
        textComponent: TextComponent,
        primaryColor: ColorHolder,
        secondaryColor: ColorHolder
    ) {}

    fun SafeClientEvent.updateActivity() {
        when (activityStatus) {
            ActivityStatus.UNINITIALIZED -> {
                initialize()
            }
            ActivityStatus.RUNNING -> {
                if (!ListenerManager.listenerMap.containsKey(this@Activity)
                    && noSubActivities()
                    && this@Activity !is DelayedActivity
                ) success()
            }
            ActivityStatus.PENDING -> {
//                refresh()
            }
            ActivityStatus.FAILURE -> {
                //
            }
        }
    }

    fun SafeClientEvent.updateTypesOnTick(activity: Activity) {
        checkTimeout(activity)
        checkDelayed(activity)
        checkRotating(activity)
    }

    fun SafeClientEvent.initialize() {
        activityStatus = ActivityStatus.RUNNING
        creationTime = System.currentTimeMillis()
        onInitialize()
        executeOnInitialize?.invoke(this)

        checkRotating(this@Activity)

//        LambdaMod.LOG.info("${System.currentTimeMillis()} Initialized $name ${System.currentTimeMillis() - ActivityManager.lastActivity.creationTime}ms after last activity creation")
    }

    fun SafeClientEvent.success() {
        val activity = this@Activity

        onSuccess()
        executeOnSuccess?.invoke(this)
        owner.subActivities.remove(activity)

        checkLoopingAmount(activity)
        checkLoopingUntil(activity)

//                LambdaMod.LOG.info("${System.currentTimeMillis()} Finalized $name after ${System.currentTimeMillis() - creationTime}ms")
//        MessageSendHelper.sendRawChatMessage("$name took ${age}ms")
    }

    fun SafeClientEvent.failedWith(exception: Exception) {
        MessageSendHelper.sendErrorMessage("Exception in $name: ${exception.message}")
        if (checkAttempt(this@Activity, exception)) return
        if (executeOnFailure?.invoke(exception) == true) return
        if (onFailure(exception)) return

        ActivityManager.reset()
    }

    fun refresh() {
        activityStatus = ActivityStatus.UNINITIALIZED
        owner.subActivities.remove(this)
        owner.subActivities.add(this)
    }

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

    enum class ActivityStatus {
        UNINITIALIZED,
        RUNNING,
        PENDING,
        FAILURE
    }

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

                if (!ActivityManagerHud.anonymize
                    || !(value is BlockPos || value is Vec3d || value is Entity || value is AxisAlignedBB)
                ) {
                    textComponent.add(name.capitalize(), primaryColor)
                    textComponent.add(value.toString(), secondaryColor)
                }
            }
        }
        addExtraInfo(textComponent, primaryColor, secondaryColor)
        textComponent.addLine("")
        subActivities.forEach {
            repeat(depth) {
                textComponent.add("   ")
            }
            it.appendInfo(textComponent, primaryColor, secondaryColor)
        }
    }

    override fun toString(): String {
        return "Name: ${javaClass.simpleName} State: $activityStatus SubActivities: $subActivities"
    }
}
