package com.lambda.module.modules.movement

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.WorldUtils.getEntities
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

// ToDo: Rework this module. All mountables should work, solution should be more elegant.
object EntityControl : Module(
    name = "EntityControl",
    description = "Control mountable entities",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val page by setting("Page", Page.GENERAL)

    /* General */
    private val forceMount by setting("Force Mount", true, description = "Attempts to force mount chested entities.", visibility = { page == Page.GENERAL }).apply {
        onValueChange { _, _ ->
            horses.forEach { horse -> horse.updateSaddle() }
        }
    }

    /* Movement */
    private val speed by setting("Entity Speed", 2.0, 0.1..10.0, 0.1, description = "Speed for entities.", visibility = { page == Page.MOVEMENT })

    private enum class Page {
        GENERAL, MOVEMENT
    }

    private val horses = mutableListOf<AbstractHorseEntity>()

    init {
        listener<TickEvent.Pre> {
            if (forceMount) {
                getEntities(player.pos, 8.0, horses, { horse, _ -> horse.setHorseFlag(4, true) })
            }
        }

        /*listener<MovementEvent.Pre> {
            if (!player.isRiding) return@listener

            // We can do this because the player movement depends on the entity movement
            player.vehicle?.motionX = speed
            player.vehicle?.motionZ = speed
        }*/

        listener<PacketEvent.Send.Pre> { event ->
            if (!forceMount) return@listener
            if (event.packet !is PlayerInteractEntityC2SPacket) return@listener
            if (event.packet.type !is PlayerInteractEntityC2SPacket.InteractAtHandler) return@listener

            val entity = world.getEntityById(event.packet.entityId) ?: return@listener
            if (entity !is AbstractHorseEntity) return@listener

            event.cancel()
        }

        onDisable {
            horses.clear()
        }
    }
}
