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
import com.lambda.util.BlockUtils.emptyState
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color
import kotlin.random.Random

data class BreakContext(
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var hotbarIndex: Int,
    var itemSelection: StackSelection,
    var instantBreak: Boolean,
    override var cachedState: BlockState,
    val sortMode: BreakConfig.SortMode
) : BuildContext(), LogContext {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val blockPos: BlockPos = result.blockPos
    override val expectedState = cachedState.emptyState

    val random = Random.nextDouble()

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                when (sortMode) {
                    BreakConfig.SortMode.Closest -> it.distance
                    BreakConfig.SortMode.Farthest -> -it.distance
                    BreakConfig.SortMode.Tool -> it.hotbarIndex != HotbarManager.serverSlot
                    BreakConfig.SortMode.Rotation -> it.rotation.target.angleDistance
                    BreakConfig.SortMode.Random -> it.random
                }
            }.thenBy {
                it.instantBreak
            }.thenByDescending {
                if (it.cachedState.block is FallingBlock) it.blockPos.y else 0
            }.thenBy {
                it.hotbarIndex == HotbarManager.serverSlot
            }.compare(this, other)

            else -> 1
        }
    }

    override fun ShapeBuilder.buildRenderer() {
        box(blockPos, cachedState, baseColor, sideColor, DirectionMask.ALL.exclude(result.side))
    }

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Break Context") {
            text(blockPos.getLogContextBuilder())
            text(result.getLogContextBuilder())
            text(rotation.getLogContextBuilder())
            value("Hotbar Index", hotbarIndex)
            value("Instant Break", instantBreak)
            value("Cached State", cachedState)
            value("Expected State", expectedState)
            value("Sort Mode", sortMode)
        }
    }
}