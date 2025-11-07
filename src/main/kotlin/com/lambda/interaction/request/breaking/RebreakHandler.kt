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

import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.request.breaking.BreakManager.calcBreakDelta
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.swingHand
import net.minecraft.util.Hand

object RebreakHandler {
    var rebreak: BreakInfo? = null

    init {
        listen<TickEvent.Post>(priority = Int.MIN_VALUE + 1) {
            rebreak?.run {
                if (!progressedThisTick) {
                    breakingTicks++
                    progressedThisTick = true
                }
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            rebreak = null
        }
    }

    context(safeContext: SafeContext)
    fun offerRebreak(info: BreakInfo) {
        if (!info.rebreakable) return

        rebreak = info.apply {
            type = BreakInfo.BreakType.Rebreak
            breaking = true
            resetCallbacks()
        }
        info.request.onReBreakStart?.invoke(safeContext, info.context.blockPos)
    }

    fun clearRebreak() {
        rebreak = null
    }

    context(_: SafeContext)
    fun BreakInfo.getRebreakPotential() = request.runSafeAutomated {
        rebreak?.let { reBreak ->
            val stack = if (breakConfig.swapMode.isEnabled())
                swapStack
            else player.mainHandStack
            val breakDelta = context.cachedState.calcBreakDelta(context.blockPos, stack)
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

    context(_: SafeContext)
    fun handleUpdate(ctx: BreakContext, breakRequest: BreakRequest) = breakRequest.runSafeAutomated {
        val reBreak = this@RebreakHandler.rebreak ?: return@runSafeAutomated RebreakResult.Ignored

        reBreak.updateInfo(ctx, breakRequest)

        val context = reBreak.context
        val breakDelta = context.cachedState.calcBreakDelta(context.blockPos)
        val breakTicks = reBreak.breakingTicks - breakConfig.fudgeFactor
        return@runSafeAutomated if (breakTicks * breakDelta >= reBreak.getBreakThreshold()) {
            if (breakConfig.breakConfirmation != BreakConfig.BreakConfirmationMode.AwaitThenBreak) {
                destroyBlock(reBreak)
            }
            reBreak.stopBreakPacket()
            if (breakConfig.swing.isEnabled()) {
                swingHand(breakConfig.swingType, Hand.MAIN_HAND)
            }
            BreakManager.breaksThisTick++
            RebreakResult.Rebroke
        } else {
            RebreakResult.StillBreaking(reBreak)
        }
    }

    enum class RebreakPotential {
        Instant,
        PartialProgress,
        None;

        fun isPossible() = this != None
    }
}