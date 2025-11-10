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

import com.lambda.Lambda.mc
import com.lambda.context.AutomationConfig
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.processing.ProcessorRegistry
import com.lambda.interaction.request.PostActionHandler
import com.lambda.util.BlockUtils.matches
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue

object InteractedBlockHandler : PostActionHandler<InteractInfo>() {
    override val pendingActions = LimitedDecayQueue<InteractInfo>(
        AutomationConfig.buildConfig.maxPendingInteractions,
        AutomationConfig.buildConfig.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.context.blockPos.toShortString()} timed out")
        if (it.interactConfirmationMode != InteractConfig.InteractConfirmationMode.AwaitThenInteract) {
            mc.world?.setBlockState(it.context.blockPos, it.context.cachedState)
        }
        it.pendingInteractionsList.remove(it.context)
    }

    init {
        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            pendingActions
                .firstOrNull { it.context.blockPos == event.pos }
                ?.let { pending ->
                    if (!pending.context.expectedState.matches(event.newState)) {
                        if (pending.context.cachedState.matches(
                                event.newState,
                                ProcessorRegistry.postProcessedProperties
                            )
                        ) {
                            pending.context.cachedState = event.newState
                            return@listen
                        }

                        pending.stopPending()

                        this@InteractedBlockHandler.warn("Interacted block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.expectedState}")
                        return@listen
                    }

                    pending.stopPending()

                    //ToDo: reliable way to recreate the sounds played when interacting with any given block
//                    if (pending.interactConfirmationMode == InteractionConfig.InteractConfirmationMode.AwaitThenInteract)
//                        with (pending.context) {
//                            cachedState.onUse(world, player, Hand.MAIN_HAND, result)
//                        }
                }
        }
    }
}