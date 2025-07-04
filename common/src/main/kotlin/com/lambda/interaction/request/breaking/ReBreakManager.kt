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

import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.player.swingHand
import net.minecraft.util.Hand

object ReBreakManager {
    var reBreak: BreakInfo? = null

    init {
        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            reBreak?.apply {
                breakingTicks++
                tickStats()
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            reBreak = null
        }
    }

    fun offerReBreak(info: BreakInfo) {
        if (!info.reBreakable) return

        reBreak = info.apply {
            type = BreakType.ReBreak
            breaking = true
            resetCallbacks()
        }
        info.request.onReBreakStart?.invoke(info.context.expectedPos)
    }

    fun clearReBreak() {
        reBreak = null
    }

    fun handleUpdate(ctx: BreakContext, breakRequest: BreakRequest) =
        runSafe {
            val info = reBreak ?: return@runSafe ReBreakResult.Ignored

            if (info.context.expectedPos != ctx.expectedPos || !info.breakConfig.reBreak) {
                return@runSafe ReBreakResult.Ignored
            }
            if (info.updatedThisTick) return@runSafe ReBreakResult.ReBroke
            info.updateInfo(ctx, breakRequest)

            val context = info.context
            val awaitThenBreak = info.breakConfig.breakConfirmation == BreakConfig.BreakConfirmationMode.AwaitThenBreak

            val breakProgress = context.cachedState.calcBlockBreakingDelta(player, world, context.expectedPos)
            return@runSafe if (info.breakingTicks * breakProgress >= info.breakConfig.breakThreshold) {
                if (context.cachedState.isEmpty) {
                    return@runSafe ReBreakResult.Ignored
                }
                if (!awaitThenBreak) {
                    destroyBlock(info)
                }
                info.stopBreakPacket(world, interaction)
                val swing = info.breakConfig.swing
                if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) {
                    swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
                }
                BreakManager.breaksThisTick++
                ReBreakResult.ReBroke
            } else {
                ReBreakResult.StillBreaking(info)
            }
        }
}