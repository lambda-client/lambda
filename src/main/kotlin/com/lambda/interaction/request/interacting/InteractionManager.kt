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

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.ManagerUtils.isPosBlocked
import com.lambda.interaction.request.ManagerUtils.newStage
import com.lambda.interaction.request.ManagerUtils.newTick
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.interacting.InteractedBlockHandler.pendingActions
import com.lambda.interaction.request.interacting.InteractedBlockHandler.setPendingConfigs
import com.lambda.interaction.request.interacting.InteractedBlockHandler.startPending
import com.lambda.interaction.request.interacting.InteractionManager.processRequest
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.module.hud.ManagerDebugLoggers.interactionManagerLogger
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.swingHand
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand

object InteractionManager : RequestHandler<InteractRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = {
        if (InteractionManager.potentialInteractions.isNotEmpty())
            InteractionManager.logger.newStage(InteractionManager.tickStage)
        InteractionManager.activeRequest?.let { it.runSafeAutomated { processRequest(it) } }
    }
), PositionBlocking, Logger {
    private var activeRequest: InteractRequest? = null
    private var potentialInteractions = mutableListOf<InteractionContext>()

    private var interactionsThisTick = 0
    private var maxInteractionsThisTick = 0

    override val blockedPositions
        get() = pendingActions.map { it.context.blockPos }

    override val logger = interactionManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            if (potentialInteractions.isNotEmpty())
                logger.newTick()
        }

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

        return "Loaded Interaction Manager"
    }

    override fun AutomatedSafeContext.handleRequest(request: InteractRequest) {
        if (activeRequest != null || request.contexts.isEmpty()) return

        activeRequest = request
        processRequest(request)
        if (interactionsThisTick > 0) activeThisTick = true
    }

    fun AutomatedSafeContext.processRequest(request: InteractRequest) {
        if (BreakManager.activeThisTick || PlaceManager.activeThisTick) return

        logger.debug("Processing request", request)

        if (request.fresh) populateFrom(request)

        if (player.isSneaking) return

        val iterator = potentialInteractions.iterator()
        while (iterator.hasNext()) {
            if (interactionsThisTick + 1 > maxInteractionsThisTick) break
            val ctx = iterator.next()

            if (!ctx.requestDependencies(request)) {
                logger.warning("Dependencies failed for interaction", ctx, request)
                return
            }
            if (tickStage !in interactConfig.tickStageMask) return

            if (interactConfig.interactConfirmationMode != InteractConfig.InteractConfirmationMode.None) {
                InteractInfo(ctx, request.pendingInteractionsList, request).startPending()
            }
            if (interactConfig.interactConfirmationMode != InteractConfig.InteractConfirmationMode.AwaitThenInteract) {
                interaction.interactBlock(player, Hand.MAIN_HAND, ctx.hitResult)
            } else {
                interaction.sendSequencedPacket(world) { sequence ->
                    PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, ctx.hitResult, sequence)
                }
            }
            if (interactConfig.swingHand) {
                swingHand(interactConfig.interactSwingType, Hand.MAIN_HAND)
            }
            request.onInteract?.invoke(ctx.blockPos)
            interactionsThisTick++
            iterator.remove()
            logger.success("interacted with ${ctx.cachedState} at ${ctx.blockPos}, changing to ${ctx.expectedState}", ctx, request)
        }
    }

    private fun Automated.populateFrom(request: InteractRequest) {
        logger.debug("Populating from request", request)
        setPendingConfigs()
        potentialInteractions = request.contexts
            .distinctBy { it.blockPos }
            .filter { !isPosBlocked(it.blockPos) }
            .take((buildConfig.maxPendingInteractions - pendingActions.size).coerceAtLeast(0))
            .toMutableList()

        logger.debug("${potentialInteractions.size} potential interactions")

        maxInteractionsThisTick = buildConfig.interactionsPerTick
    }

    override fun preEvent() = UpdateManagerEvent.Interact.post()
}
