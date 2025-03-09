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
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.warn
import net.minecraft.block.BlockState
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

data class PlaceContext(
    override val pov: Vec3d,
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override val distance: Double,
    override val expectedState: BlockState,
    override val checkedState: BlockState,
    override val hand: Hand,
    override val expectedPos: BlockPos,
    override val targetState: TargetState,
    val sneak: Boolean,
    val insideBlock: Boolean,
    val primeDirection: Direction?,
) : BuildContext {
    private val baseColor = Color(35, 188, 254, 25)
    private val sideColor = Color(35, 188, 254, 100)

    override fun interact(swingHand: Boolean) {
        runSafe {
            val actionResult = interaction.interactBlock(
                player, hand, result
            )

            if (actionResult is ActionResult.Success) {
                if (actionResult.swingSource() == ActionResult.SwingSource.CLIENT && swingHand) {
                    player.swingHand(hand)
                }

                if (!player.getStackInHand(hand).isEmpty && interaction.hasCreativeInventory()) {
                    mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
                }
            } else {
                warn("Internal interaction failed with $actionResult")
            }
        }
    }

    override fun compareTo(other: BuildContext) =
        when (other) {
            is PlaceContext -> compareBy<PlaceContext> {
                BlockUtils.fluids.indexOf(it.checkedState.fluidState.fluid)
            }.thenByDescending {
                it.checkedState.fluidState.level
            }.thenBy {
                it.hand
            }.thenBy {
                it.sneak
            }.thenBy {
                it.rotation.target.angleDistance
            }.thenBy {
                it.distance
            }.thenBy {
                it.insideBlock
            }.compare(this, other)

            else -> 1
        }

    override fun SafeContext.buildRenderer() {
        withState(expectedState, expectedPos, baseColor, DirectionMask.ALL.exclude(result.side.opposite))
        withState(blockState(result.blockPos), result.blockPos, sideColor, result.side)
    }

    override fun shouldRotate(config: BuildConfig) = config.rotateForPlace
}
