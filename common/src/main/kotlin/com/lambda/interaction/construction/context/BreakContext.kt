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

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.util.world.raycast.RayCastUtils.distanceTo
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

data class BreakContext(
    override val pov: Vec3d,
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var checkedState: BlockState,
    override val targetState: TargetState,
    override var hotbarIndex: Int,
    var instantBreak: Boolean,
) : BuildContext {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val expectedPos: BlockPos
        get() = result.blockPos

    override val distance: Double by lazy {
        result.distanceTo(pov)
    }

    fun exposedSides(ctx: SafeContext) =
        Direction.entries.filter {
            ctx.world.isAir(expectedPos.offset(it))
        }

    override val expectedState: BlockState = checkedState.fluidState.blockState

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareByDescending<BreakContext> {
                if (it.checkedState.block is FallingBlock) it.expectedPos.y else 0
            }.thenBy {
                it.instantBreak
            }.thenBy {
                it.rotation.target.angleDistance
            }.thenBy {
                it.hotbarIndex == HotbarManager.serverSlot
            }.compare(this, other)

            else -> 1
        }
    }

    override fun shouldRotate(config: BuildConfig) = config.breaking.rotateForBreak

    override fun SafeContext.buildRenderer() {
        withState(checkedState, expectedPos, baseColor, DirectionMask.ALL.exclude(result.side))
        withState(checkedState, expectedPos, sideColor, result.side)
    }

    fun requestDependencies(request: BreakRequest): Boolean {
        val hotbarRequest = request.hotbar.request(HotbarRequest(hotbarIndex, request.hotbar), false)
        return hotbarRequest.done
    }
}
