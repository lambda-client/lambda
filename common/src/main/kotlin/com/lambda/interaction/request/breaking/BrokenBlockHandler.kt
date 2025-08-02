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

package com.lambda.interaction.request.breaking

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.processing.ProcessorRegistry
import com.lambda.interaction.request.PostActionHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakManager.lastPosStarted
import com.lambda.interaction.request.breaking.BreakManager.matchesBlockItem
import com.lambda.interaction.request.breaking.ReBreakManager.reBreak
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.emptyState
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotBroken
import com.lambda.util.BlockUtils.matches
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.gamemode
import net.minecraft.block.OperatorBlock
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.ChunkSectionPos

/**
 * This object is designed to handle blocks that have been broken client side, yet are awaiting
 * confirmation from the server, and / or an item drop.
 *
 * @see BreakManager
 */
object BrokenBlockHandler : PostActionHandler<BreakInfo>() {
    override val pendingActions = LimitedDecayQueue<BreakInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info ->
        mc.world?.let { world ->
            val pos = info.context.blockPos
            val loaded = world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.x), ChunkSectionPos.getSectionCoord(pos.z))
            if (!loaded) return@let

            if (!info.broken) warn("${info.type} ${info::class.simpleName} at ${info.context.blockPos.toShortString()} timed out with cached state ${info.context.cachedState}")
            else if (!TaskFlowModule.ignoreItemDropWarnings) warn("${info.type} ${info::class.simpleName}'s item drop at ${info.context.blockPos.toShortString()} timed out")

            if (!info.broken && info.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak) {
                world.setBlockState(info.context.blockPos, info.context.cachedState)
            }
        }
        info.request.onCancel?.invoke(info.context.blockPos)
        info.pendingInteractionsList.remove(info.context)
    }

    init {
        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            run {
                pendingActions.firstOrNull { it.context.blockPos == event.pos }
                    ?: if (reBreak?.context?.blockPos == event.pos) reBreak
                    else null
            }?.let { pending ->
                val currentState = pending.context.cachedState
                // return if the block's not broken
                if (isNotBroken(currentState, event.newState)) {
                    // return if the state hasn't changed
                    if (event.newState.matches(currentState, ProcessorRegistry.postProcessedProperties)) {
                        pending.context.cachedState = event.newState
                        return@listen
                    }

                    if (pending.isReBreaking) {
                        pending.context.cachedState = event.newState
                    } else {
                        this@BrokenBlockHandler.warn("Broken block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.cachedState.emptyState}")
                        pending.stopPending()
                    }
                    return@listen
                }

                if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak
                    || pending.isRedundant
                    || (pending.isReBreaking && !pending.breakConfig.reBreak)
                    ) {
                    destroyBlock(pending)
                }
                pending.internalOnBreak()
                if (pending.callbacksCompleted) {
                    pending.stopPending()
                    if (lastPosStarted == pending.context.blockPos) {
                        ReBreakManager.offerReBreak(pending)
                    }
                }
                return@listen
            }
        }

        listen<EntityEvent.Update>(priority = Int.MIN_VALUE) {
            if (it.entity !is ItemEntity) return@listen
            run {
                pendingActions.firstOrNull { info -> matchesBlockItem(info, it.entity) }
                    ?: reBreak?.let { info ->
                        return@run if (matchesBlockItem(info, it.entity)) info
                        else null
                    }
            }?.let { pending ->
                pending.internalOnItemDrop(it.entity)
                if (pending.callbacksCompleted) {
                    pending.stopPending()
                    if (lastPosStarted == pending.context.blockPos) {
                        ReBreakManager.offerReBreak(pending)
                    }
                }
                return@listen
            }
        }
    }

    /**
     * A modified version of the minecraft breakBlock method.
     *
     * Performs the actions required to display break particles, sounds, texture overlay, etc.
     * based on the users settings.
     *
     * @return if the blocks state was set or not.
     *
     * @see net.minecraft.client.world.ClientWorld.breakBlock
     */
    fun SafeContext.destroyBlock(info: BreakInfo): Boolean {
        val ctx = info.context

        if (player.isBlockBreakingRestricted(world, ctx.blockPos, gamemode)) return false

        if (!player.mainHandStack.item.canMine(ctx.cachedState, world, ctx.blockPos, player))
            return false
        val block = ctx.cachedState.block
        if (block is OperatorBlock && !player.isCreativeLevelTwoOp) return false
        if (ctx.cachedState.isEmpty) return false

        block.onBreak(world, ctx.blockPos, ctx.cachedState, player)
        val fluidState = fluidState(ctx.blockPos)
        val setState = world.setBlockState(ctx.blockPos, fluidState.blockState, 11)
        if (setState) block.onBroken(world, ctx.blockPos, ctx.cachedState)

        if (info.breakConfig.breakingTexture) info.setBreakingTextureStage(player, world, -1)

        return setState
    }
}