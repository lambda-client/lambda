package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you"
) {
    private val flatten by setting("Flatten", false)

    init {
        listener<TickEvent.Pre> {

        }
    }
}