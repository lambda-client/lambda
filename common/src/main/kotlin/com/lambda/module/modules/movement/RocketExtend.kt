/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.filterPointer
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object RocketExtend : Module(
    name = "RocketExtend",
    description = "Extends rocket length on grim",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.GRIM)
) {
    private var extendedRockets = mutableListOf<FireworkRocketEntity>()
    private var pingPacket: CommonPongC2SPacket? = null
    private var lastPingTime = -1L
    private val keepAliveTime by setting("Keepalive Timeout", 45, 0..60, 1, unit = " s")

    init {
        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet is PlayerPositionLookS2CPacket) reset()

            if (event.packet is EntitiesDestroyS2CPacket) {
                event.packet.entityIds.map(world::getEntityById)
                    .filterPointer(extendedRockets, { _, id -> event.packet.entityIds.removeInt(id) }) { rocket ->
                        rocket.shooter == player
                    }
            }
        }

        listen<PacketEvent.Send.Pre> { event ->
            if (event.packet !is CommonPongC2SPacket) return@listen

            if (extendedRockets.isEmpty()) {
                lastPingTime = System.currentTimeMillis()
                return@listen
            }

            if (System.currentTimeMillis() - lastPingTime > keepAliveTime * 1000) {
                reset()
                return@listen
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
