
package com.minato.module.modules.movement

import com.minato.event.events.PacketEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object Velocity : Module(
    name = "Velocity",
    description = "Modifies your velocity",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val pushed by setting("Pushed", true, "Prevents the player from getting pushed by other entities")
    private val knockback by setting("Knockback", true, "Prevents the player from taking knockback when being attacked")
    @JvmStatic val explosion by setting("Explosion", true, "Prevents the player from taking knockback from explosions")

    init {
        listen<PacketEvent.Receive.Pre> { event ->
            when (event.packet) {
                is EntityVelocityUpdateS2CPacket if (knockback && event.packet.entityId == mc.player?.id) -> event.cancel()
            }
        }
    }
}
