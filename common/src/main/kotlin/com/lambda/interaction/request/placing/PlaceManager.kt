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

package com.lambda.interaction.request.placing

import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.util.Hand

object PlaceManager : RequestHandler<PlaceRequest>() {
    private val pendingInteractions = LimitedDecayQueue<PlaceContext>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.expectedPos.toShortString()} timed out") }

    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            preEvent()

            if (!updateRequest { true }) {
                postEvent()
                return@listen
            }

            currentRequest?.let { request ->
                if (request.placeContext.sneak && !player.isSneaking
                    || (request.buildConfig.placeSettings.rotateForPlace && !request.placeContext.rotation.done)
                    || (!request.hotbarConfig.request(HotbarRequest(request.placeContext.hotbarIndex)).done)
                    ) {
                    postEvent()
                    return@listen
                }
                pendingInteractions.setMaxSize(request.buildConfig.maxPendingInteractions)
                pendingInteractions.setDecayTime(request.buildConfig.interactionTimeout * 50L)
                placeBlock(request, Hand.MAIN_HAND)
            }

            postEvent()
        }

        onRotate {
            currentRequest?.let { request ->
                if (request.buildConfig.placeSettings.rotateForPlace)
                    request.rotationConfig.request(request.placeContext.rotation)
            }
        }

        listen<MovementEvent.InputUpdate> {
            if (currentRequest?.placeContext?.sneak == true) it.input.sneaking = true
        }
    }

    private fun SafeContext.placeBlock(request: PlaceRequest, hand: Hand) {
        val actionResult = interaction.interactBlock(
            player, hand, request.placeContext.result
        )

        if (actionResult.isAccepted) {
            if (actionResult.shouldSwingHand() && request.interactionConfig.swingHand) {
                player.swingHand(hand)
            }

            if (!player.getStackInHand(hand).isEmpty && interaction.hasCreativeInventory()) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
            }
        } else {
            warn("Internal interaction failed with $actionResult")
        }
        request.onPlace()
    }

    override fun preEvent() = UpdateManagerEvent.Place.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Place.Post().post()
}