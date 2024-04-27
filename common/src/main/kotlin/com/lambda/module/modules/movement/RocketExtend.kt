package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.collections.filterPointer
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object RocketExtend : Module(
    name = "RocketExtend",
    description = "Extends rocket length on grim",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private var extendedRockets = mutableListOf<FireworkRocketEntity>()
    private var pingPacket: CommonPongC2SPacket? = null
    private var lastPingTime = -1L
    private val keepAliveTime by setting("Keepalive timeout seconds", 45, 0..60, 1)

    init {
        listener<PacketEvent.Receive.Pre> { event ->
            if (event.packet is PlayerPositionLookS2CPacket) reset()

            if (event.packet is EntitiesDestroyS2CPacket) {
                event.packet.entityIds.map(world::getEntityById)
                    .filterPointer(extendedRockets, { event.packet.entityIds.removeInt(it.id) }) { rocket -> rocket.shooter == player }
            }
        }

        listener<PacketEvent.Send.Pre> { event ->
            if (event.packet !is CommonPongC2SPacket) return@listener

            if (extendedRockets.isEmpty()) {
                lastPingTime = System.currentTimeMillis()
                return@listener
            }

            if (System.currentTimeMillis() - lastPingTime > keepAliveTime * 1000) {
                reset()
                return@listener
            }

            pingPacket = event.packet
            event.cancel()
        }

        onDisable {
            reset()
        }
    }

    private fun SafeContext.reset() {
        extendedRockets.forEach(FireworkRocketEntity::discard)
        extendedRockets.clear()
        pingPacket?.let(connection::sendPacket)
        pingPacket = null
        lastPingTime = System.currentTimeMillis()
    }
}
