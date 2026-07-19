
package com.minato.module.modules.debug

import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info
import com.minato.util.combat.DamageUtils.fallDamage
import com.minato.util.combat.DamageUtils.isFallDeadly

@Suppress("unused")
object FallTest : Module(
    name = "FallTest",
    tag = ModuleTag.DEBUG,
) {
    init {
        listen<TickEvent.Pre> {
            val damage = fallDamage()

            info("Fall damage = $damage, Deadly = ${isFallDeadly()}")
        }
    }
}
