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

import com.lambda.context.Automated
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.LogContext.Companion.getLogContextBuilder
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.emptyState
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.sqrt
import kotlin.random.Random

data class BreakContext(
    override val hitResult: BlockHitResult,
    override val rotationRequest: RotationRequest,
    override val hotbarIndex: Int,
    val itemSelection: StackSelection,
    val instantBreak: Boolean,
    override var cachedState: BlockState,
    private val automated: Automated
) : BuildContext(), LogContext, Automated by automated {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val blockPos: BlockPos = hitResult.blockPos
    override val expectedState = cachedState.emptyState

    val random = Random.nextDouble()

    override fun compareTo(other: BuildContext): Int = runSafe {
        return when (other) {
            is BreakContext -> compareByDescending<BreakContext> {
                if (breakConfig.sorter == BreakConfig.SortMode.Tool) it.hotbarIndex == HotbarManager.serverSlot
                else 0
            }.thenBy {
                when (breakConfig.sorter) {
                    BreakConfig.SortMode.Tool,
                    BreakConfig.SortMode.Closest -> player.eyePos.distance(it.hitResult.pos, it.cachedState.block)
                    BreakConfig.SortMode.Farthest -> -player.eyePos.distance(it.hitResult.pos, it.cachedState.block)
                    BreakConfig.SortMode.Rotation -> it.rotationRequest.target.angleDistance
                    BreakConfig.SortMode.Random -> it.random
                }
            }.thenByDescending {
                it.hotbarIndex == HotbarManager.serverSlot
            }.thenBy {
                it.instantBreak
            }.compare(this@BreakContext, other)

            else -> 1
        }
    } ?: 0

    private fun Vec3d.distance(vec: Vec3d, block: Block): Double {
        val d = vec.x - x
        val e = (vec.y - y).let {
            if (block is FallingBlock) it - (buildConfig.attackReach / 2)
            else it
        }
        val f = vec.z - z
        return sqrt(d * d + e * e + f * f)
    }

    override fun ShapeBuilder.buildRenderer() {
        box(blockPos, cachedState, baseColor, sideColor, DirectionMask.ALL.exclude(hitResult.side))
    }

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Break Context") {
            text(blockPos.getLogContextBuilder())
            text(hitResult.getLogContextBuilder())
            text(rotationRequest.getLogContextBuilder())
            value("Hotbar Index", hotbarIndex)
            value("Instant Break", instantBreak)
            value("Cached State", cachedState)
            value("Expected State", expectedState)
        }
    }
}
