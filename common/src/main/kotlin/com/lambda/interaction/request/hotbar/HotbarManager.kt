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

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.mixin.entity.PlayerInventoryMixin
import com.lambda.mixin.render.InGameHudMixin
import com.lambda.threading.runSafe

/**
 * See mixins:
 * @see PlayerInventoryMixin.handleSpoofedMainHandStack
 * @see PlayerInventoryMixin.handleSpoofedBlockBreakingSpeed
 * @see InGameHudMixin.onTick
 */
object HotbarManager : RequestHandler<HotbarRequest>(), Loadable {
    val serverSlot get() = runSafe {
        interaction.lastSelectedSlot
    } ?: 0

    override fun load() = "Loaded Hotbar Manager"

    private var currentSlotInfo: SlotInfo? = null
    private var maxSwapsThisTick = 0
    private var swapsThisTick = 0
    private var swapDelay = 0

    fun Any.onHotbarUpdate(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Hotbar.Pre>(priority, alwaysListen) {
        block()
    }

    fun Any.onHotbarUpdatePost(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Hotbar.Post>(priority, alwaysListen) {
        block()
    }

    init {
        listen<TickEvent.Pre>(priority = Int.MIN_VALUE) {
            preEvent()

            swapsThisTick = 0
            if (swapDelay > 0) swapDelay--

            if (requestMap.isNotEmpty()) {
                val sortedRequests = requestMap.toSortedMap(compareByDescending { it.priority }).values

                sortedRequests.first().let { request ->
                    maxSwapsThisTick = request.hotbarConfig.swapsPerTick
                }

                sortedRequests.forEach { request ->
                    val actionSequence = request.actionSequence
                    HotbarActionSequence(request).apply { actionSequence() }
                }
            }
            currentSlotInfo?.let { current ->
                if (current.keepTicks <= 0) {
                    currentSlotInfo = null
                    interaction.syncSelectedSlot()
                }
            }
            requestMap.clear()
            postEvent()
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            swapsThisTick = 0
            val slotInfo = currentSlotInfo ?: return@listen

            slotInfo.swapPauseAge++
            slotInfo.activeRequestAge++
            slotInfo.keepTicks--

            if (slotInfo.keepTicks <= 0) {
                currentRequest = null
            }
        }

        listen<InventoryEvent.HotbarSlot.Update>(priority = Int.MIN_VALUE) {
            it.slot = currentSlotInfo?.slot ?: return@listen
        }
    }

    @DslMarker
    private annotation class ActionSequence

    @ActionSequence
    class HotbarActionSequence(val request: HotbarRequest) {
        @ActionSequence
        fun swapTo(
            slot: Int,
            keepTicks: Int = request.hotbarConfig.keepTicks
        ): Boolean {
            request.swapSlot = SlotInfo(slot, keepTicks, request.hotbarConfig.swapDelay)
            if (slot != currentSlotInfo?.slot) {
                if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return false

                currentSlotInfo?.let { current ->
                    if (current.activeRequestAge == 0 && (current.keepTicks > 0 || current.swapPause > 0)) {
                        request.failedSwap = true
                        return false
                    }
                }

                swapsThisTick++
                swapDelay = request.hotbarConfig.swapDelay
            } else currentSlotInfo?.let { current ->
                request.swapSlot?.swapPauseAge = current.swapPauseAge
            }
            currentSlotInfo = request.swapSlot
            mc.interactionManager?.syncSelectedSlot()
            return true
        }

        @ActionSequence
        fun done() {
            request.instantActionsComplete = true
        }
    }

    data class SlotInfo(
        val slot: Int,
        var keepTicks: Int = 3,
        var swapPause: Int = 0
    ) {
        var activeRequestAge = 0
        var swapPauseAge = 0
    }

    override fun preEvent() = UpdateManagerEvent.Hotbar.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Hotbar.Post().post()
}