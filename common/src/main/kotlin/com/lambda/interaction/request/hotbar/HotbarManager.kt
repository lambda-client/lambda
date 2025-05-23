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

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.hotbar.HotbarManager.checkResetSwap
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
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    // ToDo: Post interact
    onClose = { checkResetSwap() }
) {
    val serverSlot get() = runSafe {
        interaction.lastSelectedSlot
    } ?: 0

    private var swapsThisTick = 0
    private var maxSwapsThisTick = 0
    private var swapDelay = 0

    private var activeRequest: HotbarRequest? = null

    override fun load(): String {
        super.load()

        listen<TickEvent>(priority = Int.MAX_VALUE) {
            activeRequest?.let { activeInfo ->
                if (activeInfo.keepTicks <= 0) {
                    activeRequest = null
                }
            }
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            swapsThisTick = 0
            if (swapDelay > 0) swapDelay--
            val activeInfo = activeRequest ?: return@listen

            activeInfo.swapPauseAge++
            activeInfo.activeRequestAge++
            activeInfo.keepTicks--
        }

        listen<InventoryEvent.HotbarSlot.Update>(priority = Int.MIN_VALUE) {
            it.slot = activeRequest?.slot ?: return@listen
        }

        return "Loaded Hotbar Manager"
    }

    override fun SafeContext.handleRequest(request: HotbarRequest) {
        val config = request.hotbar
        maxSwapsThisTick = config.swapsPerTick
        swapDelay = swapDelay.coerceAtMost(config.swapDelay)

        if (tickStage !in config.sequenceStageMask) return

        if (request.slot != activeRequest?.slot) {
            if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return

            activeRequest?.let { current ->
                if (current.swappedThisTick && current.keeping) return
            }

            swapsThisTick++
            swapDelay = config.swapDelay
        } else activeRequest?.let { current ->
            request.swapPauseAge = current.swapPauseAge
            if (current.swappedThisTick && current.keeping) return
        }

        activeRequest = request
        interaction.syncSelectedSlot()
        return
    }

    private fun SafeContext.checkResetSwap() {
        activeRequest?.let { activeInfo ->
            if (activeInfo.keepTicks > 0) return

            if (tickStage in activeInfo.hotbar.sequenceStageMask) {
                interaction.syncSelectedSlot()
            }
            activeRequest = null
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Hotbar().post()
}
