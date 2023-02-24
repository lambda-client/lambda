package com.lambda.client.activity

import com.lambda.client.activity.activities.types.AttemptActivity.Companion.checkAttempt
import com.lambda.client.activity.activities.types.BuildActivity
import com.lambda.client.activity.activities.types.DelayedActivity
import com.lambda.client.activity.activities.types.DelayedActivity.Companion.checkDelayed
import com.lambda.client.activity.activities.types.EndlessActivity
import com.lambda.client.activity.activities.types.LoopWhileActivity.Companion.checkLoopingUntil
import com.lambda.client.activity.activities.types.RenderAABBActivity.Companion.checkRender
import com.lambda.client.activity.activities.types.RepeatingActivity.Companion.checkRepeat
import com.lambda.client.activity.activities.types.RotatingActivity.Companion.checkRotating
import com.lambda.client.activity.activities.types.TimeoutActivity.Companion.checkTimeout
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.MAX_DEPTH
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import net.minecraft.entity.Entity
import net.minecraft.item.ItemBlock
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.apache.commons.lang3.time.DurationFormatUtils
import java.util.concurrent.PriorityBlockingQueue

abstract class Activity(val isRoot: Boolean = false) {
    open val subActivities = PriorityBlockingQueue<Activity>(15, compareBy { it.status })
    var status = Status.UNINITIALIZED
    private var creationTime = 0L
    var owner: Activity = ActivityManager
    var depth = 0

    open fun SafeClientEvent.onInitialize() {}

    open fun SafeClientEvent.onChildInitialize(childActivity: Activity) {}

    open fun SafeClientEvent.onSuccess() {}

    open fun SafeClientEvent.onChildSuccess(childActivity: Activity) {}

    /* Return true to catch the exception */
    open fun SafeClientEvent.onFailure(exception: Exception): Boolean = false

    open fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean = false

    open fun SafeClientEvent.onCancel() {}

    open fun addExtraInfo(
        textComponent: TextComponent,
        primaryColor: ColorHolder,
        secondaryColor: ColorHolder
    ) {}

    open fun getCurrentActivity(): Activity {
        subActivities.firstOrNull()?.let {
            with(it) {
                return getCurrentActivity()
            }
        } ?: return this@Activity
    }

    val activityName get() = this.javaClass.simpleName ?: "Activity"

    val age get() = if (creationTime != 0L) System.currentTimeMillis() - creationTime else 0L

    val allSubActivities: List<Activity>
        get() = run {
            val activities = mutableListOf<Activity>()

            if (!isRoot) activities.add(this)

            activities.addAll(subActivities.flatMap { it.allSubActivities })

            activities
        }

    val hasNoSubActivities get() = subActivities.isEmpty()

    fun SafeClientEvent.updateActivity() {
        when (status) {
            Status.UNINITIALIZED -> {
                initialize()
            }
            Status.RUNNING -> {
                if (!ListenerManager.listenerMap.containsKey(this@Activity)
                    && hasNoSubActivities
                    && this@Activity !is EndlessActivity
                    && this@Activity !is DelayedActivity
                ) success()
            }
        }
    }

    fun SafeClientEvent.updateTypesOnTick(activity: Activity) {
        checkTimeout(activity)
        checkDelayed(activity)
        checkRotating(activity)
        checkRender()
    }

    fun SafeClientEvent.initialize() {
        val activity = this@Activity

        status = Status.RUNNING
        creationTime = System.currentTimeMillis()
        onInitialize()

        LambdaEventBus.subscribe(activity)

//        with(owner) {
//            onChildInitialize(activity)
//        }

        checkRotating(activity)

//        LambdaMod.LOG.info("${System.currentTimeMillis()} Initialized $name ${System.currentTimeMillis() - ActivityManager.lastActivity.creationTime}ms after last activity creation")
    }

    fun SafeClientEvent.success() {
        val activity = this@Activity

        LambdaEventBus.unsubscribe(activity)
        ListenerManager.unregister(activity)

        with(owner) {
            onChildSuccess(activity)
            subActivities.remove(activity)
        }

        onSuccess()
        checkRepeat(activity)
        checkLoopingUntil(activity)

        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()

//                LambdaMod.LOG.info("${System.currentTimeMillis()} Finalized $name after ${System.currentTimeMillis() - creationTime}ms")
//        MessageSendHelper.sendRawChatMessage("$name took ${age}ms")
    }

    fun SafeClientEvent.failedWith(exception: Exception) {
        val activity = this@Activity

        with(owner) {
            if (childFailure(ArrayDeque(listOf(activity)), exception)) return
        }

        if (checkAttempt(activity, exception)) return
        if (onFailure(exception)) return

        MessageSendHelper.sendErrorMessage("Exception in $activityName: ${exception.message}")

        ActivityManager.reset()
    }

    fun SafeClientEvent.cancel() {
        val activity = this@Activity

        subActivities.forEach {
            with(it) {
                cancel()
            }
        }

        LambdaEventBus.unsubscribe(activity)
        ListenerManager.unregister(activity)

        with(owner) {
            subActivities.remove(activity)
        }

        onCancel()

        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
    }

    private fun SafeClientEvent.childFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (onChildFailure(childActivities, childException)) return true

        if (onFailure(childException)) return true

        if (this@Activity is ActivityManager) {
            MessageSendHelper.sendErrorMessage("Traceback: ${childException.javaClass.simpleName}: ${childException.message}\n    ${childActivities.joinToString(separator = "\n    ") { it.toString() }}")
            return false
        }

        childActivities.add(this@Activity)
        with(owner) {
            childFailure(childActivities, childException)
        }
        return false
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

    enum class Status {
        RUNNING,
        UNINITIALIZED
    }

    fun appendInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder, details: Boolean) {
        if (this !is ActivityManager) {
            ListenerManager.listenerMap[this@Activity]?.let {
                textComponent.add("SYNC", primaryColor)
            }
            ListenerManager.asyncListenerMap[this@Activity]?.let {
                textComponent.add("ASYNC", primaryColor)
            }

            textComponent.add("Name", secondaryColor)
            textComponent.add("${javaClass.simpleName} ", primaryColor)
            textComponent.add("State", secondaryColor)
            textComponent.add(status.name, primaryColor)

            if (status == Status.RUNNING) {
                textComponent.add("Runtime", secondaryColor)
                textComponent.add(DurationFormatUtils.formatDuration(age, "HH:mm:ss,SSS"), primaryColor)
            }

            if (this is BuildActivity) {
                textComponent.add("Context", secondaryColor)
                textComponent.add(context.name, primaryColor)
                textComponent.add("Action", secondaryColor)
                textComponent.add(action.name, primaryColor)
            }

            textComponent.add("Hash", secondaryColor)
            textComponent.add(hashCode().toString(), primaryColor)

            if (details) {
                this::class.java.declaredFields.forEachIndexed { index, field ->
                    field.isAccessible = true
                    val name = field.name
                    val value = field.get(this)

//                    if (index.mod(6) == 0) {
//                        textComponent.addLine("", primaryColor)
//                        repeat(depth) {
//                            textComponent.add("   ")
//                        }
//                    }

                    value?.let {
                        if (!ActivityManagerHud.anonymize
                            || !(value is BlockPos || value is Vec3d || value is Entity || value is AxisAlignedBB)
                        ) {
                            textComponent.add(name.capitalize(), primaryColor)
                            when (value) {
                                is ItemBlock -> {
                                    textComponent.add(value.block.localizedName, secondaryColor)
                                }
                                else -> {
                                    textComponent.add(value.toString(), secondaryColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        addExtraInfo(textComponent, primaryColor, secondaryColor)
        textComponent.addLine("")

        subActivities.forEach {
            repeat(depth) {
                textComponent.add("   ")
            }
            it.appendInfo(textComponent, primaryColor, secondaryColor, details)
        }
    }

    override fun toString(): String {
        val properties = this::class.java.declaredFields.joinToString(separator = ", ", prefix = ", ") {
            it.isAccessible = true
            val name = it.name
            val value = it.get(this)
            "$name=$value"
        }

//        return "$activityName: [State=$activityStatus, Runtime=${DurationFormatUtils.formatDuration(age, "HH:mm:ss,SSS")}, SubActivities=${subActivities.size}$properties]"
        return "$activityName: [State=$status, Runtime=${DurationFormatUtils.formatDuration(age, "HH:mm:ss,SSS")}, SubActivities=${subActivities.size}]"
    }
}
