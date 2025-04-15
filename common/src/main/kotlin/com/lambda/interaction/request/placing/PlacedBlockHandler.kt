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

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.placing.PlaceManager.placeSound
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.item
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.block.BlockState
import net.minecraft.item.BlockItem
import net.minecraft.util.math.BlockPos

object PlacedBlockHandler {
    val pendingPlacements = LimitedDecayQueue<PlaceInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out")
        mc.world?.setBlockState(it.context.expectedPos, it.context.checkedState)
        it.pendingInteractionsList.remove(it.context)
    }

    init {
        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            pendingPlacements
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { info ->
                    removePendingPlace(info)

                    // return if the block wasn't placed
                    if (!matchesTargetState(event.pos, info.context.targetState, event.newState))
                        return@listen

                    if (info.placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
                        with (info.context) {
                            placeSound(expectedState.block.item as BlockItem, expectedState, expectedPos)
                        }
                    info.onPlace()
                    return@listen
                }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre> {
            pendingPlacements.clear()
        }
    }

    /**
     * Adds the info to the [PlacedBlockHandler], and requesters, pending interaction collections.
     */
    fun addPendingPlace(info: PlaceInfo) {
        pendingPlacements.add(info)
        info.pendingInteractionsList.add(info.context)
    }

    /**
     * Removes the info from the [PlacedBlockHandler], and requesters, pending interaction collections.
     */
    private fun removePendingPlace(info: PlaceInfo) {
        pendingPlacements.remove(info)
        info.pendingInteractionsList.remove(info.context)
    }

    /**
     * Sets the size limit and decay time for the [pendingPlacements] using the [request]'s configs
     */
    fun setPendingConfigs(request: PlaceRequest) {
        pendingPlacements.setSizeLimit(request.build.placing.maxPendingPlacements)
        pendingPlacements.setDecayTime(request.build.interactionTimeout * 50L)
    }

    /**
     * @return if the [targetState] matches the [newState]
     *
     * @see TargetState
     */
    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@PlacedBlockHandler.warn("Place at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }
}