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

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.buildLogContext
import com.lambda.interaction.request.LogContext.Companion.toLogContext
import com.lambda.interaction.request.Request.Companion.submit
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.util.BlockUtils
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color

data class PlaceContext(
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var hotbarIndex: Int,
    override val blockPos: BlockPos,
    override var cachedState: BlockState,
    override val expectedState: BlockState,
    val sneak: Boolean,
    val insideBlock: Boolean,
    val currentDirIsValid: Boolean = false
) : BuildContext(), LogContext {
    private val baseColor = Color(35, 188, 254, 25)
    private val sideColor = Color(35, 188, 254, 100)

    override fun compareTo(other: BuildContext) =
        when (other) {
            is PlaceContext -> compareBy<PlaceContext> {
                BlockUtils.fluids.indexOf(it.cachedState.fluidState.fluid)
            }.thenByDescending {
                if (it.cachedState.fluidState.level != 0) it.blockPos.y else 0
            }.thenByDescending {
                it.cachedState.fluidState.level
            }.thenBy {
                it.sneak == (mc.player?.isSneaking ?: false)
            }.thenBy {
                it.rotation.target.angleDistance
            }.thenBy {
                it.hotbarIndex == HotbarManager.serverSlot
            }.thenBy {
                it.distance
            }.thenBy {
                it.insideBlock
            }.compare(this, other)

            else -> 1
        }

    override fun ShapeBuilder.buildRenderer() {
        box(blockPos, expectedState, baseColor, sideColor, result.side.mask)
    }

    fun requestDependencies(request: PlaceRequest): Boolean {
        val hotbarRequest = submit(HotbarRequest(hotbarIndex, request.hotbar), false)
        val validRotation = if (request.rotateForPlace) {
            submit(rotation, false).done && currentDirIsValid
        } else true
        return hotbarRequest.done && validRotation
    }

    override fun toLogContext() =
        buildLogContext {
            group("Place Context") {
                text(blockPos.toLogContext())
                text(result.toLogContext())
                text(rotation.toLogContext())
                value("Hotbar Index", hotbarIndex)
                value("Cached State", cachedState)
                value("Expected State", expectedState)
                value("Sneak", sneak)
                value("Inside Block", insideBlock)
                value("Current Dir Is Invalid", currentDirIsValid)
            }
        }
}
