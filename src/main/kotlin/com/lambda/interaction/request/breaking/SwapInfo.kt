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

package com.lambda.interaction.request.breaking

import com.lambda.Lambda.mc
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Rebreak
import com.lambda.interaction.request.breaking.BreakManager.calcBreakDelta
import com.lambda.interaction.request.breaking.BreakManager.currentStack
import com.lambda.module.modules.client.TaskFlowModule
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.world.BlockView

data class SwapInfo(
    private val type: BreakInfo.BreakType,
    private val breakConfig: BreakConfig = TaskFlowModule.build.breaking,
    val swap: Boolean = false,
    val minKeepTicks: Int = 0,
) {
    val validSwap
        get() = run {
            val serverSwapTicks = if (type == Primary || type == Rebreak) breakConfig.serverSwapTicks
            else breakConfig.serverSwapTicks.coerceAtLeast(3)

            (mc.player?.mainHandStack?.heldTicks ?: return false) >= serverSwapTicks
        }

    companion object {
        val EMPTY = SwapInfo(Primary)

        fun getSwapInfo(
            info: BreakInfo,
            player: ClientPlayerEntity,
            world: BlockView
        ): SwapInfo = with(info) {
            val breakDelta = context.cachedState
                .calcBreakDelta(player, world, context.blockPos, breakConfig, swapStack)

            val threshold = getBreakThreshold()

            // Plus one as this is calculated before this ticks progress is calculated and the breakingTicks are incremented
            val breakTicks = (if (rebreakPotential.isPossible()) RebreakHandler.rebreak?.breakingTicks
                ?: throw IllegalStateException("Rebreak BreakInfo was null when rebreak was considered possible")
            else breakingTicks) + 1 - breakConfig.fudgeFactor

            val minKeepTicks = run {
                if (type == Primary) {
                    val swapTickProgress = breakDelta * (breakTicks + breakConfig.serverSwapTicks - 1).coerceAtLeast(1)
                    if (swapTickProgress >= threshold && swapStack.heldTicks < breakConfig.serverSwapTicks) 1
                    else 0
                } else {
                    val serverSwapTicks = breakConfig.serverSwapTicks.coerceAtLeast(3)
                    val swapTickProgress = breakDelta * (breakTicks + serverSwapTicks - 1).coerceAtLeast(1)
                    if (swapTickProgress >= threshold && swapStack.heldTicks < serverSwapTicks) 1
                    else 0
                }
            }

            val swapAtEnd = breakDelta * breakTicks >= threshold || minKeepTicks > 0

            val swap = if (rebreakPotential == RebreakHandler.RebreakPotential.Instant)
                breakConfig.swapMode.isEnabled()
            else when (breakConfig.swapMode) {
                BreakConfig.SwapMode.None -> false
                BreakConfig.SwapMode.Start -> !breaking
                BreakConfig.SwapMode.End -> swapAtEnd
                BreakConfig.SwapMode.StartAndEnd -> !breaking || swapAtEnd
                BreakConfig.SwapMode.Constant -> true
            }

            return SwapInfo(info.type, breakConfig, swap, minKeepTicks)
        }

        private val ItemStack.heldTicks
            get() = if (this == currentStack)
                BreakManager.heldTicks
            else 0
    }
}