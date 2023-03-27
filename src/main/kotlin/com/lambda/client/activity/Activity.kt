package com.lambda.client.activity

import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.activity.types.AttemptActivity.Companion.checkAttempt
import com.lambda.client.activity.types.BuildActivity
import com.lambda.client.activity.types.DelayedActivity
import com.lambda.client.activity.types.DelayedActivity.Companion.checkDelayed
import com.lambda.client.activity.types.LoopWhileActivity.Companion.checkLoopingUntil
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.activity.types.RenderAABBActivity.Companion.checkAABBRender
import com.lambda.client.activity.types.RepeatingActivity.Companion.checkRepeat
import com.lambda.client.activity.types.RotatingActivity.Companion.checkRotating
import com.lambda.client.activity.types.TimeoutActivity.Companion.checkTimeout
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.MAX_DEPTH
import com.lambda.client.module.AbstractModule
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

abstract class Activity {
    val subActivities = ArrayDeque<Activity>()
    var status = Status.UNINITIALIZED
    private var creationTime = 0L
    var parent: Activity? = null
    var owner: AbstractModule? = null
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

            parent?.let {
                activities.add(this)
            }

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
                    && this@Activity !is DelayedActivity
                ) success()
            }
        }
    }

    fun SafeClientEvent.updateTypesOnTick(activity: Activity) {
        checkTimeout(activity)
        checkDelayed(activity)
        checkRotating(activity)
        checkAABBRender()
    }

    fun SafeClientEvent.initialize() {
        val activity = this@Activity

        status = Status.RUNNING
        creationTime = System.currentTimeMillis()
        onInitialize()

        LambdaEventBus.subscribe(activity)

//        if (!owner.isRoot) {
//            with(owner) {
//                onChildInitialize(activity)
//            }
//        }

        checkRotating(activity)

//        LambdaMod.LOG.info("${System.currentTimeMillis()} Initialized $name ${System.currentTimeMillis() - ActivityManager.lastActivity.creationTime}ms after last activity creation")
    }

    fun SafeClientEvent.success() {
        val activity = this@Activity

        LambdaEventBus.unsubscribe(activity)
        ListenerManager.unregister(activity)

        if (activity is RenderAABBActivity) {
            activity.aabbCompounds.clear()
        }

        parent?.let {
            with(it) {
                onChildSuccess(activity)
                subActivities.remove(activity)
            }
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

        MessageSendHelper.sendErrorMessage("Exception in $activityName: ${exception.message}")

        if (onFailure(exception)) return

        parent?.let {
            with(it) {
                if (childFailure(ArrayDeque(listOf(activity)), exception)) return
            }
        }

        if (checkAttempt(activity, exception)) return

        MessageSendHelper.sendErrorMessage("Fatal Exception in $activityName: ${exception.message}")

        with(ActivityManager) {
            cancel()
        }
    }

    fun SafeClientEvent.cancel() {
        val activity = this@Activity

        onCancel()

        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()

        subActivities.toList().forEach {
            with(it) {
                cancel()
            }
        }

        parent?.let {
            with(it) {
                LambdaEventBus.unsubscribe(activity)
                ListenerManager.unregister(activity)
                subActivities.remove(activity)
            }
        }
    }

    private fun SafeClientEvent.childFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (onChildFailure(childActivities, childException)) return true

        if (onFailure(childException)) return true

        if (this@Activity is ActivityManager) {
            MessageSendHelper.sendErrorMessage("Traceback: ${childException.javaClass.simpleName}: ${childException.message}\n    ${childActivities.joinToString(separator = "\n    ") { it.toString() }}")
            return false
        }

        childActivities.add(this@Activity)
        parent?.let {
            with(it) {
                childFailure(childActivities, childException)
            }
        }

        return false
    }

    fun Activity.addSubActivities(activities: List<Activity>, subscribe: Boolean = false, module: AbstractModule? = null) {
        if (activities.isEmpty()) return

        if (depth > MAX_DEPTH) {
            MessageSendHelper.sendErrorMessage("Activity depth exceeded $MAX_DEPTH!")
            ActivityManager.reset()
            return
        }

        activities.forEach { activity ->
            activity.parent = this
            activity.owner = module
            activity.depth = depth + 1
            if (subscribe) LambdaEventBus.subscribe(activity)
        }

        subActivities.addAll(activities)

//        LambdaMod.LOG.info("${System.currentTimeMillis()} Added ${activities.size} sub activities to $name")
    }

    fun Activity.addSubActivities(vararg activities: Activity, subscribe: Boolean = false) {
        addSubActivities(activities.toList(), subscribe)
    }

    fun AbstractModule.addSubActivities(vararg activities: Activity, subscribe: Boolean = false) {
        addSubActivities(activities.toList(), subscribe, this)
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

            owner?.let {
                textComponent.add("Module", secondaryColor)
                textComponent.add(it.name, primaryColor)
            }

            textComponent.add("Name", secondaryColor)
            textComponent.add("${javaClass.simpleName} ", primaryColor)

            if (this is BuildActivity) {
                textComponent.add("Context", secondaryColor)
                textComponent.add(context.name, primaryColor)
                textComponent.add("Availability", secondaryColor)
                textComponent.add(availability.name, primaryColor)
                textComponent.add("Type", secondaryColor)
                textComponent.add(type.name, primaryColor)
            }

            textComponent.add("State", secondaryColor)
            textComponent.add(status.name, primaryColor)

            if (status == Status.RUNNING) {
                textComponent.add("Runtime", secondaryColor)
                textComponent.add(DurationFormatUtils.formatDuration(age, "HH:mm:ss,SSS"), primaryColor)
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

        val acti = if (this is BuildStructure) {
            subActivities
                .filterIsInstance<BuildActivity>()
                .sortedWith(buildComparator())
                .filterIsInstance<Activity>()
        } else {
            subActivities
        }

        acti.forEach {
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
