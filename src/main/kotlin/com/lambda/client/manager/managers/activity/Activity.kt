package com.lambda.client.manager.managers.activity

import com.lambda.client.LambdaMod
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.activity.activities.*
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.text.capitalize
import net.minecraft.util.math.BlockPos
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
                        when (it.activityStatus) {
                            ActivityStatus.SUCCESS -> {
                                finalize(this@Activity)
                            }
                            ActivityStatus.FAILURE -> {
                                subActivities.remove(it)
                                LambdaMod.LOG.error("Activity failed: $it")
                            }
                            else -> {
                                updateActivities()
                            }
                        }
                    }
                } ?: run {
                    if (this@Activity is TimeoutActivity) {
                        if (System.currentTimeMillis() > creationTime + timeout) {
                            if (this@Activity is AttemptActivity) {
                                if (usedAttempts >= maxAttempts) {
                                    activityStatus = ActivityStatus.FAILURE
                                    LambdaMod.LOG.error("TimedActivity fully timed out!")
                                } else {
                                    usedAttempts++
                                    initialize()
                                    LambdaMod.LOG.error("TimedActivity timed out!")
                                }
                            } else {
                                activityStatus = ActivityStatus.FAILURE
                                LambdaMod.LOG.error("TimedActivity fully timed out!")
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
                            LambdaMod.LOG.error("AttemptActivity failed after $maxAttempts attempts!")
                        }
                    }
                }
            }
            ActivityStatus.SUCCESS -> {
                // do nothing
            }
            ActivityStatus.FAILURE -> {
                finalize(this@Activity)
            }
        }
    }

    private fun SafeClientEvent.initialize() {
        if (this@Activity is TimeoutActivity) {
            creationTime = System.currentTimeMillis()
        }
        if (this@Activity is DelayedActivity) {
            creationTime = System.currentTimeMillis()
        }
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
        return subActivities.peek()?.currentActivity() ?: this
    }

    fun reset() {
        LambdaEventBus.unsubscribe(currentActivity())
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
        subActivities.clear()
    }

    inline fun addSubActivity(block: () -> Activity) {
        subActivities.add(block())
    }

    override fun toString(): String {
        return "Name: ${javaClass.simpleName} State: $activityStatus SubActivities: $subActivities"
    }

    open fun addExtraInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder) {}

    fun appendInfo(textComponent: TextComponent, primaryColor: ColorHolder, secondaryColor: ColorHolder, depth: Int) {
        if (this !is ActivityManager) {
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
        subActivities.forEach {
            repeat(depth) {
                textComponent.add("   ")
            }
            it.appendInfo(textComponent, primaryColor, secondaryColor, depth + 1)
        }
    }
}