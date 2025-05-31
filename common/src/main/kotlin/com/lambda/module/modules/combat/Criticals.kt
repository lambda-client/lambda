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

package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.rotation
import com.lambda.util.math.component1
import com.lambda.util.math.component2
import com.lambda.util.math.component3
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object Criticals : Module(
    name = "Criticals",
    description = "Forces your hits to be critical",
    defaultTags = setOf(ModuleTag.COMBAT)
) {
    private val mode by setting("Mode", Mode.Grim)

    enum class Mode {
        Grim
    }

    init {
        listen<PlayerEvent.Attack.Entity> {
            when (mode) {
                Mode.Grim -> {
                    if (player.isOnGround) posPacket(0.00000001, rotation = player.rotation)
                    posPacket(-0.000000001, rotation = player.eyePos.rotationTo(it.entity.boundingBox.center))

                    connection.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, player.yaw, player.pitch)) // TODO: This is wrong, fix it
                    connection.sendPacket(
                        PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN,
                            Direction.DOWN
                        )
                    )
                }
            }
        }
    }

    private fun SafeContext.posPacket(yOffset: Double, ground: Boolean = false, rotation: Rotation?) {
        val (x, y, z) = player.pos

        val packet = rotation?.let {
            PlayerMoveC2SPacket.Full(x, y + yOffset, z, it.yawF, it.pitchF, ground, true) // TODO: Check this after update
        } ?: PlayerMoveC2SPacket.PositionAndOnGround(x, y + yOffset, z, ground, true) // TODO: Check this after update

        connection.sendPacket(packet)
    }
}
