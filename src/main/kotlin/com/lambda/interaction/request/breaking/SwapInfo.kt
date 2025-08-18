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

import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakManager.currentStack
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.world.BlockView

data class SwapInfo(
    val type: BreakInfo.BreakType,
    val breakConfig: BreakConfig = TaskFlowModule.build.breaking,
    val swap: Boolean = false,
    val minKeepTicks: Int = 0,
) {
    val canCompleteBreak
        get() = (BreakManager.heldTicks + 1) >= if (type == Primary) breakConfig.serverSwapTicks
        else breakConfig.serverSwapTicks.coerceAtLeast(2)

    companion object {
        val EMPTY = SwapInfo(Primary)

        fun getSwapInfo(
            info: BreakInfo,
            player: ClientPlayerEntity,
            world: BlockView
        ): SwapInfo = with(info) {
            val breakDelta = context.cachedState
                .calcItemBlockBreakingDelta(player, world, context.blockPos, swapStack)
            val breakDeltaNoEfficiency = context.cachedState
                .calcItemBlockBreakingDelta(player, world, context.blockPos, swapStack, ignoreEfficiency = true)
            val breakTicks = (if (rebreakPotential.isPossible()) RebreakManager.rebreak?.breakingTicks
                ?: throw IllegalStateException("Rebreak BreakInfo was null when rebreak was considered possible")
            else breakingTicks).let {
                // Plus one as this is calculated before this ticks progress is calculated and the breakingTicks are incremented
                (it + 1) - breakConfig.fudgeFactor
            }
            val threshold = getBreakThreshold()

            val minKeepTicks = run {
                if (type == Primary) {
                    val swapTickProgress = breakDelta * (breakTicks + breakConfig.serverSwapTicks - 1)
                    val withoutEfficiency = breakDeltaNoEfficiency * breakTicks >= threshold
                    if (swapTickProgress >= threshold &&
                        !withoutEfficiency) 1
                    else 0
                } else {
                    val serverSwapTicks = breakConfig.serverSwapTicks.coerceAtLeast(2)
                    val swapTickProgress = breakDelta * (breakTicks + serverSwapTicks - 1)
                    if (swapTickProgress >= threshold && swapStack.heldTicks < serverSwapTicks) 1
                    else 0
                }
            }

            val swapAtEnd = breakDelta * breakTicks >= threshold || minKeepTicks > 0

            val swap = if (rebreakPotential == RebreakManager.RebreakPotential.Instant)
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
            get() = if (currentStack == this)
                BreakManager.heldTicks
            else 0
    }
}