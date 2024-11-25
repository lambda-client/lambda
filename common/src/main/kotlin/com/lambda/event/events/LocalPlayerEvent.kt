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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class LocalPlayerEvent {
    /**
     * Represents the local player moving the cursor around
     */
    data class ChangeLookDirection(
        val deltaYaw: Double,
        val deltaPitch: Double,
    ) : ICancellable by Cancellable()

    /**
     * Represents the local player swinging its hand
     */
    data class SwingHand(
        val hand: Hand
    ) : ICancellable by Cancellable()

    /**
     * Represents the local player attacking an entity
     */
    data class EntityAttack(
        val entity: Entity
    ) : ICancellable by Cancellable()

    /**
     * Represents the local player interacting (right-click) with blocks
     */
    data class BlockInteract(
        val world: ClientWorld,
        val blockHitResult: BlockHitResult
    ) : Event

    /**
     * Represents the local player attacking a block
     */
    data class BlockAttack(
        val pos: BlockPos,
        val side: Direction
    ) : ICancellable by Cancellable()

    /**
     * Represents events triggered during a block breaking action
     */
    data class BreakingProgress(
        val pos: BlockPos,
        val side: Direction,
        var progress: Float,
    ) : ICancellable by Cancellable()

    /**
     * Represents the local player clicking on a slot in a screen
     */
    data class SlotClick(
        val syncId: Int,
        val slot: Int,
        val button: Int,
        val action: SlotActionType,
        val screenHandler: ScreenHandler,
    ) : ICancellable by Cancellable()
}
