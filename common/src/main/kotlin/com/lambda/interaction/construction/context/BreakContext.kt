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
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotating.RotationRequest
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color

data class BreakContext(
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var hotbarIndex: Int,
    override var cachedState: BlockState,
    override val targetState: TargetState,
    var instantBreak: Boolean
) : BuildContext() {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val expectedPos: BlockPos
        get() = result.blockPos

    override val expectedState: BlockState = cachedState.fluidState.blockState

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareByDescending<BreakContext> {
                if (it.cachedState.block is FallingBlock) it.expectedPos.y else 0
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

    override fun SafeContext.buildRenderer() {
        withState(cachedState, expectedPos, baseColor, DirectionMask.ALL.exclude(result.side))
        withState(cachedState, expectedPos, sideColor, result.side)
    }

    fun requestDependencies(breakRequest: BreakRequest, minKeepTicks: Int = 0): Boolean {
        val request = HotbarRequest(hotbarIndex, breakRequest.hotbar, breakRequest.hotbar.keepTicks.coerceAtLeast(minKeepTicks))
        return request.hotbar.request(request, false).done
    }
}
