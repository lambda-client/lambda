package com.lambda.client.manager.managers

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.BuildActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity.Companion.checkRender
import com.lambda.client.activity.activities.types.TimedActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.executionCountPerTick
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    private val renderer = ESPRenderer()
    const val MAX_DEPTH = 25

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (hasNoSubActivities || event.phase != TickEvent.Phase.START) return@safeListener

            val allActivities = allSubActivities

            allActivities
                .filter { it.status == Status.RUNNING && it.subActivities.isEmpty() }
                .forEach {
                    with(it) {
                        updateTypesOnTick(it)
                    }
                }

            var lastActivity: Activity? = null

//            BaritoneUtils.settings?.allowPlace?.value = false
//            BaritoneUtils.settings?.allowBreak?.value = false
            BaritoneUtils.settings?.allowInventory?.value = false

            repeat(executionCountPerTick) {
                val current = getCurrentActivity()

                with(current) {
                    // ToDo: Find a working way to guarantee specific age of activity
                    (lastActivity as? TimedActivity)?.let {
                        if (age < it.earliestFinish) return@repeat
                    }

                    updateActivity()
                    checkRender()
                }

                lastActivity = current
            }
        }

        safeListener<RenderWorldEvent> {
            if (hasNoSubActivities) return@safeListener

            renderer.aFilled = BuildTools.aFilled
            renderer.aOutline = BuildTools.aOutline
            renderer.thickness = BuildTools.thickness

            RenderAABBActivity.normalizedRender.forEach { renderAABB ->
                renderer.add(renderAABB.renderAABB, renderAABB.color)
            }

            renderer.render(true)
        }
    }

    fun reset() {
        ListenerManager.listenerMap.keys.filterIsInstance<Activity>().forEach {
            it.owner?.let { _ ->
                LambdaEventBus.unsubscribe(it)
                ListenerManager.unregister(it)
            }
        }
        ListenerManager.asyncListenerMap.keys.filterIsInstance<Activity>().forEach {
            it.owner?.let { _ ->
                LambdaEventBus.unsubscribe(it)
                ListenerManager.unregister(it)
            }
        }
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
        subActivities.clear()
    }
}