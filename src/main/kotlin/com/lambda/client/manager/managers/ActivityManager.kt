package com.lambda.client.manager.managers

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.LambdaEventBus.subscribedListeners
import com.lambda.client.event.LambdaEventBus.subscribedListenersAsync
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.manager.managers.ActivityManager.updateTypesOnTick
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    private val renderer = ESPRenderer()
    const val MAX_DEPTH = 25
    private var lastActivity: Activity = this

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (noSubActivities() || event.phase != TickEvent.Phase.START) return@safeListener

            val currentActivity = currentActivity

            getAllSubActivities().filter { it.activityStatus == ActivityStatus.RUNNING
                || it.activityStatus == ActivityStatus.PENDING }.forEach {
                with(it) {
                    updateTypesOnTick(it)
                }
            }

            with(currentActivity) {
                if (activityStatus == ActivityStatus.RUNNING
                    || activityStatus == ActivityStatus.PENDING) updateTypesOnTick(currentActivity)
            }

            repeat(100) {
                updateCurrentActivity()
            }
        }

        safeListener<RenderWorldEvent> {
            if (noSubActivities()) return@safeListener

            renderer.aFilled = BuildTools.aFilled
            renderer.aOutline = BuildTools.aOutline
            renderer.thickness = BuildTools.thickness

            RenderAABBActivity.normalizedRender.forEach { renderAABB ->
                renderer.add(renderAABB.renderAABB, renderAABB.color)
            }

            renderer.render(true)
        }
    }

    private fun SafeClientEvent.updateCurrentActivity() {
        val currentActivity = currentActivity

        with(currentActivity) {
            BaritoneUtils.settings?.allowPlace?.value = false
            BaritoneUtils.settings?.allowBreak?.value = false
            BaritoneUtils.settings?.allowInventory?.value = false

//            if (currentActivity != lastActivity) {
//                if (lastActivity !is ActivityManager && lastActivity.activityStatus != ActivityStatus.PENDING) {
////                if (lastActivity !is ActivityManager) {
//                    LambdaEventBus.unsubscribe(lastActivity)
//                    ListenerManager.unregister(lastActivity)
//                }
//
//                LambdaEventBus.subscribe(currentActivity)
//                BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
//
//                lastActivity = currentActivity
//            }

            updateActivity()
        }
    }

    fun reset() {
//        if (lastActivity !is ActivityManager && lastActivity.activityStatus != ActivityStatus.PENDING)
        ListenerManager.listenerMap.keys.filterIsInstance<Activity>().forEach {
            if (it is ActivityManager) return@forEach
            LambdaEventBus.unsubscribe(it)
            ListenerManager.unregister(it)
        }
        ListenerManager.asyncListenerMap.keys.filterIsInstance<Activity>().forEach {
            if (it is ActivityManager) return@forEach
            LambdaEventBus.unsubscribe(it)
            ListenerManager.unregister(it)
        }
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
        subActivities.clear()

        lastActivity = ActivityManager
    }
}