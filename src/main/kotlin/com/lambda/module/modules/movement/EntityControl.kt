/*
 * Copyright 2025 Lambda
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

import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

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
