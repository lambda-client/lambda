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

package com.lambda.interaction.managers.breaking

import com.lambda.config.AutomationConfig.Companion.DEFAULT
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.Secondary
import com.lambda.interaction.managers.breaking.BreakManager.calcBreakDelta
import com.lambda.threading.runSafeAutomated

/**
 * A simple data class to store info about when the [BreakManager] should swap tool.
 */
data class SwapInfo(
	private val type: BreakInfo.BreakType,
	private val automated: Automated = DEFAULT,
	val swap: Boolean = false,
	val longSwap: Boolean = false
) : Automated by automated {
	companion object {
		val EMPTY = SwapInfo(Primary)

		/**
		 * Calculates the contents and returns a [SwapInfo].
		 */
		context(_: SafeContext)
		fun BreakInfo.getSwapInfo() = request.runSafeAutomated {
			val breakDelta = context.cachedState.calcBreakDelta(context.blockPos, swapStack)

			val threshold = getBreakThreshold()

			// Plus one as this is calculated before this ticks' progress is calculated and the breakingTicks are incremented
			val breakTicks = (if (rebreakPotential.isPossible()) RebreakHandler.rebreak?.breakingTicks
				?: throw IllegalStateException("Rebreak BreakInfo was null when rebreak was considered possible")
			else breakingTicks) + 1 - breakConfig.fudgeFactor

			val swapAtEnd = run {
				val swapTickProgress = if (type == Primary)
					breakDelta * (breakTicks + breakConfig.serverSwapTicks - 1).coerceAtLeast(1)
				else {
					val serverSwapTicks = hotbarConfig.swapPause.coerceAtLeast(3)
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

			SwapInfo(
				type, this, swap,
				breakConfig.serverSwapTicks > 0 || (type == Secondary && swapAtEnd)
			)
		}
	}
}