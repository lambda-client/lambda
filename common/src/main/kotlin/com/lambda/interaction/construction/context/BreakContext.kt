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

package com.lambda.interaction.construction.context

import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.util.world.raycast.RayCastUtils.distanceTo
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class BreakContext(
    override val pov: Vec3d,
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override val checkedState: BlockState,
    override var hand: Hand,
    val instantBreak: Boolean,
) : BuildContext {
    override val expectedPos: BlockPos
        get() = result.blockPos

    override val distance: Double by lazy {
        result.distanceTo(pov)
    }

    fun exposedSides(ctx: SafeContext) =
        Direction.entries.filter {
            ctx.world.isAir(result.blockPos.offset(it))
        }

    override val expectedState: BlockState = checkedState.fluidState.blockState

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                it.distance
            }.compare(this, other)

            else -> 1
        }
    }

    override fun SafeContext.buildRenderer() {

    }
}
