
package com.minato.interaction.managers.breaking

import com.minato.config.automation.AutomationConfig.Companion.DEFAULT
import com.minato.config.blocks.BreakConfig
import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.interaction.handlers.breaking.RebreakHandler
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.Primary
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.Secondary
import com.minato.interaction.managers.breaking.BreakManager.calcBreakDelta
import com.minato.threading.runSafeAutomated

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
					breakDelta * (breakTicks + (breakConfig.serverSwapTicks - 1).coerceAtLeast(0)).coerceAtLeast(1)
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