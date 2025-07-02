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
import com.lambda.config.groups.InteractionConfig
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.matches
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object InteractedBlockHandler {
    val pendingInteractions = LimitedDecayQueue<InteractionInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out")
        if (it.interact.interactConfirmationMode != InteractionConfig.InteractConfirmationMode.AwaitThenInteract) {
            mc.world?.setBlockState(it.context.expectedPos, it.context.checkedState)
        }
        it.pendingInteractionsList.remove(it.context)
    }

    init {
        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            pendingInteractions
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { info ->
                    removePendingInteract(info)

                    if (!matchesTargetState(event.pos, info.context.expectedState, event.newState))
                        return@listen

                    if (info.interact.interactConfirmationMode == InteractionConfig.InteractConfirmationMode.AwaitThenInteract)
                        with (info.context) {
                            checkedState.onUse(world, player, Hand.MAIN_HAND, result)
                        }
                }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre> {
            pendingInteractions.clear()
        }
    }

    fun addPendingInteract(info: InteractionInfo) {
        pendingInteractions.add(info)
        info.pendingInteractionsList.add(info.context)
    }

    fun removePendingInteract(info: InteractionInfo) {
        pendingInteractions.remove(info)
        info.pendingInteractionsList.remove(info.context)
    }

    fun setPendingConfigs(request: InteractionRequest) {
        pendingInteractions.setSizeLimit(request.build.maxPendingInteractions)
        pendingInteractions.setDecayTime(request.build.interactionTimeout * 50L)
    }

    private fun matchesTargetState(pos: BlockPos, targetState: BlockState, newState: BlockState) =
        if (targetState.matches(newState)) true
        else {
            this@InteractedBlockHandler.warn("Interaction at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }
}