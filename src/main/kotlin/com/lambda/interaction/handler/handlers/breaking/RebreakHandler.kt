/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.handler.handlers.breaking

import com.lambda.config.blocks.BreakConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.handler.handlers.PacketLimitHandler
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.handler.handlers.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.interaction.handler.handlers.breaking.RebreakHandler.reBreak
import com.lambda.interaction.manager.managers.breaking.BreakInfo
import com.lambda.interaction.manager.managers.breaking.BreakManager
import com.lambda.interaction.manager.managers.breaking.BreakManager.calcBreakDelta
import com.lambda.interaction.manager.managers.breaking.BreakRequest
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.PlayerUtils.swingHand
import net.minecraft.util.Hand

/**
 * Designed to track the latest primary-broken [BreakInfo] in order to exploit a flaw in Minecraft's code that allows
 * the user to break any block placed in said position using the progress from the previously broken block.
 */
internal object RebreakHandler {
	var reBreak: BreakInfo? = null

    init {
        listen<TickEvent.Post>({ Int.MIN_VALUE + 1 }) {
            reBreak?.run {
                if (!progressedThisTick) {
                    breakingTicks++
                    progressedThisTick = true
                }
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
            reBreak = null
        }
    }

	/**
	 * Tests to see if the [BreakInfo] can be accepted. If not, nothing happens. Otherwise,
	 * the [reBreak] is set, and the [com.lambda.interaction.managers.breaking.BreakRequest.onReBreakStart] callback is invoked.
	 */
	context(safeContext: SafeContext)
	fun offerRebreak(info: BreakInfo) {
		if (!info.rebreakable) return

		reBreak = info.apply {
			type = BreakInfo.BreakType.Rebreak
			breaking = true
			resetCallbacks()
		}
		info.request.onReBreakStart?.invoke(safeContext, info.context.blockPos)
	}

	fun clearRebreak() {
		reBreak = null
	}

	/**
	 * [RebreakPotential.None] if it cannot be rebroken at all.
	 *
	 * [RebreakPotential.PartialProgress] if some progress would be added to the break.
	 *
	 * [RebreakPotential.Instant] if the block can be instantly rebroken.
	 *
	 * @return In what way this block can be rebroken.
	 */
	context(_: SafeContext)
	fun BreakInfo.getRebreakPotential() = request.runSafeAutomated {
		reBreak?.let { reBreak ->
			val stack = if (breakConfig.swapMode.isEnabled())
				swapStack
			else player.mainHandStack
			val breakDelta = calcBreakDelta(stack)
			val possible = reBreak.breakConfig.rebreak &&
					context.blockPos == reBreak.context.blockPos
			val instant = (reBreak.breakingTicks - breakConfig.fudgeFactor) * breakDelta >= breakConfig.breakThreshold
			when {
				possible && instant -> RebreakPotential.Instant
				possible -> RebreakPotential.PartialProgress
				else -> RebreakPotential.None
			}
		} ?: RebreakPotential.None
	}

	/**
	 * Updates the current [reBreak] with a fresh [BreakContext], and attempts to rebreak the block if possible.
	 *
	 * @return A [RebreakResult] to indicate how the update has been processed.
	 */
	context(_: SafeContext)
	fun handleUpdate(ctx: BreakContext, request: BreakRequest) = request.runSafeAutomated {
		val reBreak = this@RebreakHandler.reBreak ?: return@runSafeAutomated RebreakResult.Ignored

		reBreak.updateInfo(ctx, request)

		val breakDelta = reBreak.calcBreakDelta()
		val breakTicks = reBreak.breakingTicks - breakConfig.fudgeFactor
		return@runSafeAutomated if (breakTicks * breakDelta >= reBreak.getBreakThreshold()) {
			if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return@runSafeAutomated RebreakResult.Ignored
			if (breakConfig.breakConfirmation != BreakConfig.BreakConfirmationMode.AwaitThenBreak) {
				destroyBlock(reBreak)
			}
			reBreak.stopBreakPacket()
			PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
			if (breakConfig.swing.isEnabled()) {
				swingHand(breakConfig.swingType, Hand.MAIN_HAND)
			}
			BreakManager.breaksThisTick++
			RebreakResult.Rebroke
		} else {
			RebreakResult.StillBreaking(reBreak)
		}
	}
}

enum class RebreakPotential {
	Instant,
	PartialProgress,
	None;

	fun isPossible() = this != None
}