
package com.minato.module.modules.debug

import com.minato.event.events.ClientEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.event.listener.SafeListener.Companion.listenConcurrently
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info

@Suppress("unused")
object TimerTest : Module(
    name = "TimerTest",
    tag = ModuleTag.DEBUG,
) {
    private var last = 0L

    init {
        listen<ClientEvent.FixedTick> {
            val now = System.currentTimeMillis()
            info("${now - last} - Fixed Tick on game thread")
            last = now
        }

        listenConcurrently<ClientEvent.FixedTick> {
            // Concurrent handler (runs off the game thread)
        }
    }
}
