package com.lambda.module.modules.movement

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.WorldUtils.getEntities
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

object HorseUtils : Module(
    name = "HorseUtils",
    description = "Various utilities for horses.",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.BYPASS)
) {
    private val page by setting("Page", Page.General)

    /* General */
    private val forceMount by setting("Force Mount", true, description = "Attempts to force mount chested entities.", visibility = { page == Page.General })
    private val tameHorses by setting("Tame Horses", true, description = "Automatically tames horses (client-side only).", visibility = { page == Page.General })

    /* Rendering */
    private val showInfo by setting("Show Info", true, description = "Renders information about entities.", visibility = { page == Page.Rendering })

    private val theHonses = mutableListOf<AbstractHorseEntity>() // Petah, the honse is here
    private val tame: (AbstractHorseEntity) -> Unit = { horse -> if (tameHorses) horse.setHorseFlag(4, true) }

    private enum class Page {
        General, Rendering
    }

    init {
        listener<TickEvent.Pre> {
            getEntities(theHonses, iterator = tame)
        }

        listener<PacketEvent.Send.Pre> { event ->
            if (!forceMount) return@listener
            if (event.packet !is PlayerInteractEntityC2SPacket) return@listener
            if (event.packet.type !is PlayerInteractEntityC2SPacket.InteractAtHandler) return@listener

            val entity = world.getEntityById(event.packet.entityId) ?: return@listener
            if (entity !is AbstractHorseEntity) return@listener

            event.cancel()
        }
    }
}
