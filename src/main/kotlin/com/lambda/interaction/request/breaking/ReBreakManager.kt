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
import com.lambda.interaction.request.breaking.BreakManager.calcBreakDelta
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.player.swingHand
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.Hand
import net.minecraft.world.BlockView

object ReBreakManager {
    var reBreak: BreakInfo? = null

    init {
        listen<TickEvent.Pre>(priority = Int.MIN_VALUE) {
            reBreak?.run {
                if (!progressedThisTick) {
                    breakingTicks++
                    progressedThisTick = true
                }
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
        info.request.onReBreakStart?.invoke(info.context.blockPos)
    }

    fun clearReBreak() {
        reBreak = null
    }

    fun couldReBreak(info: BreakInfo, player: ClientPlayerEntity, world: BlockView) =
        reBreak?.let { reBreak ->
            val stack = if (info.breakConfig.swapMode.isEnabled())
                player.inventory.getStack(info.context.hotbarIndex)
            else player.mainHandStack
            val breakDelta = info.context.cachedState.calcItemBlockBreakingDelta(player, world, info.context.blockPos, stack)
            reBreak.breakConfig.reBreak &&
                    info.context.blockPos == reBreak.context.blockPos &&
                    !reBreak.updatedThisTick &&
                    ((reBreak.breakingTicks - info.breakConfig.fudgeFactor) * breakDelta >= info.breakConfig.breakThreshold)
        } == true

    fun handleUpdate(ctx: BreakContext, breakRequest: BreakRequest) =
        runSafe {
            val reBreak = this@ReBreakManager.reBreak ?: return@runSafe ReBreakResult.Ignored

            reBreak.updateInfo(ctx, breakRequest)

            val context = reBreak.context
            val breakDelta = context.cachedState.calcBreakDelta(player, world, context.blockPos, reBreak.breakConfig)
            return@runSafe if ((reBreak.breakingTicks - reBreak.breakConfig.fudgeFactor) * breakDelta >= reBreak.breakConfig.breakThreshold) {
                if (reBreak.breakConfig.breakConfirmation != BreakConfig.BreakConfirmationMode.AwaitThenBreak) {
                    destroyBlock(reBreak)
                }
                reBreak.stopBreakPacket(world, interaction)
                if (reBreak.breakConfig.swing.isEnabled()) {
                    swingHand(reBreak.breakConfig.swingType, Hand.MAIN_HAND)
                }
                BreakManager.breaksThisTick++
                ReBreakResult.ReBroke
            } else {
                ReBreakResult.StillBreaking(reBreak)
            }
        }
}