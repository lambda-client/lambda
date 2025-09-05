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
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.ManagerUtils.newStage
import com.lambda.interaction.request.ManagerUtils.newTick
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.hotbar.HotbarManager.checkResetSwap
import com.lambda.module.hud.ManagerDebugLoggers.hotbarManagerLogger
import com.lambda.threading.runSafe

object HotbarManager : RequestHandler<HotbarRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { if (HotbarManager.activeRequest != null) HotbarManager.logger.newStage(HotbarManager.tickStage) },
    onClose = { checkResetSwap() }
), Logger {
    val serverSlot get() = runSafe {
        interaction.lastSelectedSlot
    } ?: 0

    private var swapsThisTick = 0
    private var maxSwapsThisTick = 0
    private var swapDelay = 0

    var activeRequest: HotbarRequest? = null

    override val logger = hotbarManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            if (activeRequest != null)
                logger.newTick()
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            swapsThisTick = 0
            if (swapDelay > 0) swapDelay--
            val activeInfo = activeRequest ?: return@listen

            activeInfo.swapPauseAge++
            activeInfo.activeRequestAge++
            activeInfo.keepTicks--
        }

        return "Loaded Hotbar Manager"
    }

    override fun SafeContext.handleRequest(request: HotbarRequest) {
        logger.debug("Handling request:", request.toLogContext())

        maxSwapsThisTick = request.swapsPerTick
        swapDelay = swapDelay.coerceAtMost(request.swapDelay)

        if (tickStage !in request.sequenceStageMask) return

        val sameButLonger = activeRequest?.let { active ->
            request.slot == active.slot && request.keepTicks >= active.keepTicks
        } == true

        if (sameButLonger) activeRequest?.let { current ->
            request.swapPauseAge = current.swapPauseAge
            logger.debug("Request is the same as current, but longer or the same keep time", request.toLogContext())
        } else run swap@{
            if (request.slot != activeRequest?.slot) {
                if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return

                activeRequest?.let { current ->
                    if (current.swappedThisTick && current.keeping) return
                }

                swapsThisTick++
                swapDelay = request.swapDelay
                return@swap
            }

            activeRequest?.let { current ->
                request.swapPauseAge = current.swapPauseAge
                if (current.swappedThisTick && current.keeping) return
            }
        }

        activeRequest = request
        logger.success("Set active request", request.toLogContext())
        interaction.syncSelectedSlot()
        return
    }

    private fun SafeContext.checkResetSwap() {
        activeRequest?.let { active ->
            val canStopSwap = swapsThisTick < maxSwapsThisTick
            if (active.keepTicks <= 0 && tickStage in active.sequenceStageMask && canStopSwap) {
                logger.debug("Clearing request and syncing slot", activeRequest?.toLogContext())
                activeRequest = null
                interaction.syncSelectedSlot()
            }
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Hotbar.post()
}
