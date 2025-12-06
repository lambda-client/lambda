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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d

sealed class PlayerPacketEvent {
    data class Pre(
        var position: Vec3d,
        var rotation: Rotation,
        var onGround: Boolean,
        var isSprinting: Boolean,
        var isCollidingHorizontally: Boolean,
    ) : ICancellable by Cancellable()

    class Post : Event

    data class Send(
        val packet: PlayerMoveC2SPacket,
    ) : ICancellable by Cancellable()
}
