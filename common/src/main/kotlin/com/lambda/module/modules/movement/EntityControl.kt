package com.lambda.module.modules.movement

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.entitySearch
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

// ToDo: Rework this module. All mountables should work, solution should be more elegant.
object EntityControl : Module(
    name = "EntityControl",
    description = "Control mountable entities",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val forceMount by setting("Force Mount", true, description = "Attempts to force mount chested entities.").apply {
        onValueChange { _, _ ->
            resetMounts()
        }
    }
    private val modified = mutableSetOf<AbstractHorseEntity>()

    init {
        listener<TickEvent.Pre> {
            if (!forceMount) return@listener

            entitySearch<AbstractHorseEntity>(8.0)
                .forEach {
                    it.setHorseFlag(4, true)
                    modified.add(it)
                }
        }

        listener<PacketEvent.Send.Pre> { event ->
            if (!forceMount) return@listener
            if (event.packet !is PlayerInteractEntityC2SPacket) return@listener
            if (event.packet.type !is PlayerInteractEntityC2SPacket.InteractAtHandler) return@listener

            val entity = world.getEntityById(event.packet.entityId) ?: return@listener
            if (entity !is AbstractHorseEntity) return@listener

            event.cancel()
        }

        onDisable {
            resetMounts()
        }
    }

    private fun resetMounts() {
        modified.forEach { it.updateSaddle() }
    }
}
