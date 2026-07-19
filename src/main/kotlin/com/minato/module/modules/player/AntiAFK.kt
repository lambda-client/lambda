
package com.minato.module.modules.player

import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import net.minecraft.util.Hand

@Suppress("unused")
object AntiAFK : Module(
    name = "AntiAFK",
    description = "Keeps you from getting kicked",
    tag = ModuleTag.PLAYER,
) {
    private val delay by setting("Delay", 300, 5..600, 1, unit = " s", description = "Delay between swinging the hand.")
    private val swingHand by setting("Swing Hand", Hand.MAIN_HAND, description = "Hand to swing.")

    init {
        listen<TickEvent.Pre> {
            if (mc.uptimeInTicks % (delay * 20) != 0L) return@listen
            player.swingHand(swingHand)
        }
    }
}
