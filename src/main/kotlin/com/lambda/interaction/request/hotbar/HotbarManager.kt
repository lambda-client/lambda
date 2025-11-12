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

import com.lambda.context.AutomatedSafeContext
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
import com.lambda.interaction.request.hotbar.HotbarManager.activeRequest
import com.lambda.interaction.request.hotbar.HotbarManager.checkResetSwap
import com.lambda.interaction.request.hotbar.HotbarManager.maxSwapsThisTick
import com.lambda.interaction.request.hotbar.HotbarManager.setActiveRequest
import com.lambda.module.hud.ManagerDebugLoggers.hotbarManagerLogger
import com.lambda.threading.runSafe
import net.minecraft.item.ItemStack

/**
 * Manager responsible for handling the current selected hotbar index. It can be accessed from anywhere through a
 * [HotbarRequest]
 *
 * "Silent swapping" is a feature of this manager. If requested with a [HotbarRequest] that has [HotbarRequest.keepTicks]
 * set to 0, assuming the request is accepted, the manager will only swap for the duration of the current [tickStage].
 * After which, the manager will end the request and swap back to the player's selected slot.
 */
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
    //ToDo: something to manage stacks so the hotbar manager is strictly index based
    private var previousStack: ItemStack? = null
    private var swappedTicks = 0

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

            val currentStack = player.mainHandStack
            if (previousStack != currentStack) swappedTicks = 1
            else swappedTicks++
            previousStack = currentStack

            val activeInfo = activeRequest ?: return@listen
            activeInfo.swapPauseAge = swappedTicks
            activeInfo.activeRequestAge++
            activeInfo.keepTicks--
        }

        return "Loaded Hotbar Manager"
    }

    /**
     * Attempts to accept the request and process it. If the [activeRequest] is not null, the new [request] matches hotbar index,
     * and the new request has an equal or longer [HotbarRequest.keepTicks] than the current request, the new request is accepted.
     * Otherwise, if the [activeRequest] is null, or is from an old request, assuming the swap doesn't exceed [maxSwapsThisTick],
     * the request is accepted.
     *
     * @see setActiveRequest
     */
    override fun AutomatedSafeContext.handleRequest(request: HotbarRequest) {
        logger.debug("Handling request:", request)

        if (tickStage !in hotbarConfig.sequenceStageMask) return

        activeRequest?.let { active ->
            if (request.slot == serverSlot && request.keepTicks >= active.keepTicks) {
                logger.debug("Request is the same as current, but longer or the same keep time", request)
                setActiveRequest(request)
                return
            }

            if (active.activeRequestAge <= 0 && active.keepTicks > 0) return
        } ?: run { maxSwapsThisTick = hotbarConfig.swapsPerTick }

        if (request.slot != serverSlot)
            if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return

        setActiveRequest(request)
    }

    /**
     * Sets the [activeRequest]. This also calls syncSelectedSlot to
     * update the server now to keep predictability.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.syncSelectedSlot
     */
    private fun AutomatedSafeContext.setActiveRequest(request: HotbarRequest) {
        maxSwapsThisTick = hotbarConfig.swapsPerTick
        if (request.slot != serverSlot) {
            swapsThisTick++
            swappedTicks = 0
            swapDelay = hotbarConfig.swapDelay
        } else request.swapPauseAge = swappedTicks
        activeRequest = request
        interaction.syncSelectedSlot()
        logger.success("Set active request", request)
    }

    /**
     * Called after every [tickStage] closes. This method checks if the current [activeRequest] should be stopped.
     * This action is counted as another swap, so the conditions for a regular swap must be met. If the requests
     * [HotbarConfig.sequenceStageMask] does not contain the current tick stage, no actions can be performed.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.syncSelectedSlot
     */
    private fun SafeContext.checkResetSwap() {
        activeRequest?.let { active ->
            val canStopSwap = swapsThisTick < maxSwapsThisTick
            if (active.keepTicks <= 0 && tickStage in active.hotbarConfig.sequenceStageMask && canStopSwap) {
                logger.debug("Clearing request and syncing slot", activeRequest)
                activeRequest = null
                interaction.syncSelectedSlot()
            }
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Hotbar.post()
}
