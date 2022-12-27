package com.lambda.client.manager.managers

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.RenderBlockActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.Manager
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

            runActivity()
        }

        safeListener<RenderWorldEvent> {
            val currentActivity = currentActivity()

            if (currentActivity !is RenderBlockActivity) return@safeListener

            renderer.aFilled = 26
            renderer.aOutline = 91
            renderer.thickness = 2.0f
            renderer.add(currentActivity.renderBlockPos, currentActivity.color)
            renderer.render(true)
        }
    }

    fun SafeClientEvent.runActivity() {
        with(currentActivity()) {
            updateListener()
            updateActivity()
        }
    }

    fun reset() {
        if (lastActivity !is ActivityManager) {
            LambdaEventBus.unsubscribe(lastActivity)
            ListenerManager.unregister(lastActivity)
        }
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
        subActivities.clear()

        lastActivity = ActivityManager
    }

    private fun updateListener() {
        val currentActivity = currentActivity()

        if (currentActivity == lastActivity) return

        if (lastActivity !is ActivityManager) {
            LambdaEventBus.unsubscribe(lastActivity)
            ListenerManager.unregister(lastActivity)
        }

        LambdaEventBus.subscribe(currentActivity)
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()

        lastActivity = currentActivity
    }
}