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

package com.lambda.interaction.construction.context

import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.interacting.InteractionRequest
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color

class InteractionContext(
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var hotbarIndex: Int,
    override val cachedState: BlockState,
    override val expectedState: BlockState,
) : BuildContext() {
    private val baseColor = Color(35, 254, 79, 25)
    private val sideColor = Color(35, 254, 79, 100)

    override val blockPos: BlockPos = result.blockPos

    override fun compareTo(other: BuildContext) =
        when {
            other is InteractionContext -> compareBy<BuildContext> {
                BlockUtils.fluids.indexOf(it.cachedState.fluidState.fluid)
            }.thenByDescending {
                it.cachedState.fluidState.level
            }.thenBy {
                it.rotation.target.angleDistance
            }.thenBy {
                it.hotbarIndex == HotbarManager.serverSlot
            }.thenBy {
                it.distance
            }.compare(this, other)

            else -> 1
        }

    override fun SafeContext.buildRenderer() {
        withState(expectedState, blockPos, baseColor, DirectionMask.ALL.exclude(result.side.opposite))
        withState(blockState(result.blockPos), result.blockPos, sideColor, result.side)
    }

    fun requestDependencies(request: InteractionRequest): Boolean {
        val hotbarRequest = request.hotbar.request(HotbarRequest(hotbarIndex, request.hotbar), false)
        val validRotation = if (request.interact.rotate) {
            request.rotation.request(rotation, false).done
        } else true
        return hotbarRequest.done && validRotation
    }
}