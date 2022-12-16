package com.lambda.client.manager.managers

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.RenderBlockActivity
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    private val renderer = ESPRenderer()

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (subActivities.isEmpty() || it.phase != TickEvent.Phase.START) return@safeListener

            updateActivities()
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

    fun addActivity(activity: Activity) {
        subActivities.add(activity)
    }
}