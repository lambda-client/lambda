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

package com.lambda.interaction.request.interacting

import com.lambda.config.groups.InteractionConfig
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.interacting.InteractedBlockHandler.addPendingInteract
import com.lambda.interaction.request.interacting.InteractedBlockHandler.pendingInteractions
import com.lambda.interaction.request.interacting.InteractedBlockHandler.setPendingConfigs
import com.lambda.interaction.request.interacting.InteractionManager.activeRequest
import com.lambda.interaction.request.interacting.InteractionManager.processRequest
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.placing.PlacedBlockHandler.pendingPlacements
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand

object InteractionManager : RequestHandler<InteractionRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { activeRequest?.let { processRequest(it) } }
) {
    private var activeRequest: InteractionRequest? = null
    private var potentialInteractions = mutableListOf<InteractionContext>()

    private var interactionsThisTick = 0
    private var maxInteractionsThisTick = 0

    init {
        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            activeRequest = null
            interactionsThisTick = 0
            potentialInteractions.clear()
        }

        listen<MovementEvent.InputUpdate>(priority = Int.MIN_VALUE) {
            if (potentialInteractions.isNotEmpty()) {
                it.input.sneaking = false
            }
        }
    }

    override fun SafeContext.handleRequest(request: InteractionRequest) {
        if (activeRequest != null || BreakManager.activeThisTick || PlaceManager.activeThisTick) return

        activeRequest = request
        processRequest(request)
        if (interactionsThisTick > 0) activeThisTick = true
    }

    fun SafeContext.processRequest(request: InteractionRequest) {
        pendingInteractions.cleanUp()
        
        if (request.fresh) populateFrom(request)

        if (player.isSneaking) return

        val iterator = potentialInteractions.iterator()
        while (iterator.hasNext()) {
            if (interactionsThisTick + 1 > maxInteractionsThisTick) break
            val interact = request.interact
            val ctx = iterator.next()

            if (!ctx.requestDependencies(request)) return

            if (interact.interactConfirmationMode == InteractionConfig.InteractConfirmationMode.None) {
                addPendingInteract(InteractionInfo(ctx, request.pendingInteractionsList, interact))
            }
            if (interact.interactConfirmationMode != InteractionConfig.InteractConfirmationMode.AwaitThenInteract) {
                interaction.interactBlock(player, Hand.MAIN_HAND, ctx.result)
            } else {
                interaction.sendSequencedPacket(world) { sequence ->
                    PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, ctx.result, sequence)
                }
            }
            request.onInteract?.invoke(ctx.expectedPos)
            interactionsThisTick++
            iterator.remove()
        }
    }

    private fun populateFrom(request: InteractionRequest) {
        setPendingConfigs(request)
        potentialInteractions = request.contexts.toMutableList()

        val pendingLimit =  (request.build.maxPendingInteractions - pendingPlacements.size).coerceAtLeast(0)
        maxInteractionsThisTick = (request.build.interactionsPerTick.coerceAtMost(pendingLimit))
    }

    override fun preEvent() = UpdateManagerEvent.Interact.post()
}