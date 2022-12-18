package com.lambda.client.manager.managers.activity.activities.example

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

class DoWorkyActivity : Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(FailingActivity())
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener


        }
    }
}