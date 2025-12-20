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

package com.lambda.interaction.construction.simulation.context

import com.lambda.context.Automated
import com.lambda.graphics.esp.ShapeScope
import com.lambda.graphics.mc.TransientRegionESP
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.managers.LogContext
import com.lambda.interaction.managers.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.managers.LogContext.Companion.getLogContextBuilder
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.emptyState
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color
import kotlin.math.sqrt

data class BreakContext(
    override val hitResult: BlockHitResult,
    override val rotationRequest: RotationRequest,
    override val hotbarIndex: Int,
    val itemSelection: StackSelection,
    val instantBreak: Boolean,
    val insideBlock: Boolean,
    override var cachedState: BlockState,
    private val automated: Automated
) : BuildContext(), LogContext, Automated by automated {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val blockPos: BlockPos = hitResult.blockPos
    override val expectedState = cachedState.emptyState
    override val sortDistance = runSafe {
        val pov = player.eyePos
        val vec = hitResult.pos
        val d = vec.x - pov.x
        val e = (vec.y - pov.y).let {
            if (cachedState.block is FallingBlock) it - (buildConfig.attackReach / 2)
            else it
        }
        val f = vec.z - pov.z
        sqrt(d * d + e * e + f * f)
    } ?: Double.MAX_VALUE

    override val sorter get() = breakConfig.sorter

    override fun render(esp: TransientRegionESP) {
        esp.shapes(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble()) {
            box(blockPos, baseColor, sideColor)
        }
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
