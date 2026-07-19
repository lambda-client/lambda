
package com.minato.module.modules.movement

import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.world.fastEntitySearch
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

@Suppress("unused")
object EntityControl : Module(
    name = "EntityControl",
    description = "Control mountable entities",
    tag = ModuleTag.MOVEMENT,
) {
    private val forceMount by setting("Force Mount", true, description = "Attempts to force mount chested entities.")

    private val saddledHorses = mutableSetOf<AbstractHorseEntity>()

    init {
        listen<TickEvent.Pre> {
            fastEntitySearch<AbstractHorseEntity>(8.0)
                .forEach {
                    if (!it.hasSaddleEquipped()) saddledHorses.add(it)
                    it.setHorseFlag(4, true)
                }
        }

        listen<PacketEvent.Send.Pre> { event ->
            if (!forceMount) return@listen
            if (event.packet !is PlayerInteractEntityC2SPacket) return@listen
            if (event.packet.type !is PlayerInteractEntityC2SPacket.InteractAtHandler) return@listen

            val entity = world.getEntityById(event.packet.entityId) ?: return@listen
            if (entity !is AbstractHorseEntity) return@listen

            event.cancel()
        }

        onDisable {
            saddledHorses.forEach { it.setHorseFlag(4, false) }
            saddledHorses.clear()
        }
    }
}
