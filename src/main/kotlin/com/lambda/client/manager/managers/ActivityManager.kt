package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.RenderBlockActivity
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object ActivityManager : Manager, Activity() {
    private val renderer = ESPRenderer()
    const val MAX_DEPTH = 25

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (noSubActivities() || event.phase != TickEvent.Phase.START) return@safeListener

            val currentActivity = currentActivity()

//            ListenerManager.listenerMap.keys.filterIsInstance<Activity>().filter { it !is ActivityManager && it != currentActivity }.forEach {
//                ListenerManager.unregister(it)
//                LambdaMod.LOG.info("Unsubscribed ${it::class.simpleName}")
//            }

            with(currentActivity) {
                updateActivity()
            }
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
}