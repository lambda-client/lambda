
package com.minato.module.modules.movement

import com.minato.event.events.ClientEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag

@Suppress("unused")
object Timer : Module(
    name = "Timer",
    description = "Modify client tick speed.",
    tag = ModuleTag.MOVEMENT,
) {
    private val timer by setting("Timer", 1.0, 0.0..10.0, 0.01)

    init {
        listen<ClientEvent.TimerUpdate> {
            it.speed = timer.coerceAtLeast(0.05)
        }
    }
}
