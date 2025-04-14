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

package com.lambda.interaction.request.hotbar

import com.lambda.config.groups.TickStage
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.hotbar.HotbarManager.checkResetSwap
import com.lambda.interaction.request.hotbar.HotbarManager.preEvent
import com.lambda.mixin.entity.PlayerInventoryMixin
import com.lambda.mixin.render.InGameHudMixin
import com.lambda.threading.runSafe

/**
 * See mixins:
 * @see PlayerInventoryMixin.handleSpoofedMainHandStack
 * @see PlayerInventoryMixin.handleSpoofedBlockBreakingSpeed
 * @see InGameHudMixin.onTick
 */
object HotbarManager : RequestHandler<HotbarRequest>(
    *TickStage.entries.toTypedArray(),
    postClose = { checkResetSwap() },
    onOpen = { preEvent() }
), Loadable {
    val serverSlot get() = runSafe {
        interaction.lastSelectedSlot
    } ?: 0

    override fun load() = "Loaded Hotbar Manager"

    private var swapsThisTick = 0
    private var maxSwapsThisTick = 0
    private var swapDelay = 0

    private var activeRequest: HotbarRequest? = null

    init {
        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            swapsThisTick = 0
            if (swapDelay > 0) swapDelay--
            val slotInfo = activeRequest ?: return@listen

            slotInfo.swapPauseAge++
            slotInfo.activeRequestAge++
            slotInfo.keepTicks--

            if (slotInfo.keepTicks <= 0) {
                activeRequest = null
            }
        }

        listen<InventoryEvent.HotbarSlot.Update>(priority = Int.MIN_VALUE) {
            it.slot = activeRequest?.slot ?: return@listen
        }
    }

    override fun SafeContext.handleRequest(request: HotbarRequest) {
        val hotbar = request.hotbar
        maxSwapsThisTick = hotbar.swapsPerTick
        swapDelay = swapDelay.coerceAtMost(hotbar.swapDelay)

        if (tickStage !in hotbar.sequenceStageMask) return

        if (request.slot != activeRequest?.slot) {
            if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return

            activeRequest?.let { current ->
                if (current.swappedThisTick && current.keeping) return
            }

            swapsThisTick++
            swapDelay = hotbar.swapDelay
        } else activeRequest?.let { current ->
            request.swapPauseAge = current.swapPauseAge
            if (current.swappedThisTick && current.keeping) return
        }

        activeRequest = request
        interaction.syncSelectedSlot()
        return
    }

    private fun SafeContext.checkResetSwap() {
        activeRequest?.let { active ->
            if (active.keepTicks <= 0) {
                activeRequest = null
                interaction.syncSelectedSlot()
            }
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Hotbar().post()
}