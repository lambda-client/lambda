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

import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Secondary
import com.lambda.interaction.request.breaking.BreakManager.calcBreakDelta
import com.lambda.module.modules.client.TaskFlowModule
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.world.BlockView

data class SwapInfo(
    private val type: BreakInfo.BreakType,
    private val breakConfig: BreakConfig = TaskFlowModule.build.breaking,
    val swap: Boolean = false,
    val longSwap: Boolean = false
) : LogContext {
    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Swap Info") {
            value("Type", type)
            value("Swap", swap)
        }
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

            val swapAtEnd = run {
                val swapTickProgress = if (type == Primary)
                    breakDelta * (breakTicks + request.config.serverSwapTicks - 1).coerceAtLeast(1)
                else {
                    val serverSwapTicks = request.hotbar.swapPause.coerceAtLeast(3)
                    breakDelta * (breakTicks + serverSwapTicks - 1).coerceAtLeast(1)
                }
                swapTickProgress >= threshold
            }

            val swap = when (breakConfig.swapMode) {
                BreakConfig.SwapMode.None -> false
                BreakConfig.SwapMode.Start -> !breaking
                BreakConfig.SwapMode.End -> swapAtEnd
                BreakConfig.SwapMode.StartAndEnd -> !breaking || swapAtEnd
                BreakConfig.SwapMode.Constant -> true
            }

            return SwapInfo(
                info.type,
                request.config,
                swap,
                breakConfig.serverSwapTicks > 0 || (info.type == Secondary && swapAtEnd)
            )
        }
    }
}