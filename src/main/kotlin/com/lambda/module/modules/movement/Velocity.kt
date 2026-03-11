/*
 * Copyright 2026 Lambda
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

import com.lambda.Lambda.mc
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
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
        listenUnsafe <PacketEvent.Receive.Pre> { event ->
            when (event.packet) {
                is EntityVelocityUpdateS2CPacket if (knockback && event.packet.entityId == mc.player?.id) -> event.cancel()
            }
        }
    }
}
