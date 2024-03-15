package com.lambda.module.modules.movement

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object RocketExtend : Module(
    name = "RocketExtend",
    description = "Extends rocket length on grim",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    var extendedRockets: MutableList<FireworkRocketEntity> = mutableListOf();
    var pingPacket: CommonPongC2SPacket? = null;
    init {
        onDisable {
            if (extendedRockets.isNotEmpty()) reset()
        }
        listener<PacketEvent.Receive.Pre> { event ->
            when (event.packet) {
                is EntitiesDestroyS2CPacket -> {
                    val rockets = event.packet.entityIds.map { id ->
                        world.getEntityById(id)
                    }.filter { entity ->
                        entity is FireworkRocketEntity && entity.shooter?.equals(this.player) == true
                    }.filterNotNull().map {it as FireworkRocketEntity}

                    if (rockets.isEmpty()) return@listener
                    extendedRockets.addAll(rockets)
                    event.packet.entityIds.removeAll(rockets.map { it.id }.toSet())

                    this@RocketExtend.info("RocketExtend triggered")
                }
                is PlayerPositionLookS2CPacket -> {
                    if(extendedRockets.isNotEmpty()) reset()
                }
            }
        }
        listener<PacketEvent.Send.Pre> { event ->
            when (event.packet) {
                is CommonPongC2SPacket -> {
                    if (extendedRockets.isNotEmpty()) {
                        pingPacket = event.packet

                        event.cancel()
                    }
                }
            }
        }
    }

    fun reset() {
        this@RocketExtend.info("Reset RocketExtend")

        runSafe {
            extendedRockets.forEach { it.discard() }
            if (pingPacket != null) connection.sendPacket(pingPacket)
        }

        extendedRockets.clear()
        pingPacket = null
    }
}