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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object Velocity : Module(
    name = "Velocity",
    description = "Modifies your velocity",
    defaultTags = setOf(ModuleTag.MOVEMENT),
) {
    private val knockback by setting("Knockback", true)

    private val explosionSetting by setting("Explosion", true)
    @JvmStatic val explosion get() = isEnabled && explosionSetting

    init {
        listen<PacketEvent.Receive.Pre> { event ->
            if (!knockback) return@listen
            if (event.packet !is EntityVelocityUpdateS2CPacket) return@listen
            if (event.packet.id != player.id) return@listen

            event.cancel()
        }
    }
}
