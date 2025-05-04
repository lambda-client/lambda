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
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakManager.lastPosStarted
import com.lambda.interaction.request.breaking.BreakManager.matchesBlockItem
import com.lambda.interaction.request.breaking.ReBreakManager.reBreak
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.BlockUtils.matches
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.gamemode
import net.minecraft.block.BlockState
import net.minecraft.block.OperatorBlock
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.ChunkSectionPos

/**
 * This object is designed to handle blocks that have been broken client side, yet are awaiting
 * confirmation from the server, and / or an item drop.
 *
 * @see BreakManager
 */
object BrokenBlockHandler {
    val pendingBreaks = LimitedDecayQueue<BreakInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info ->
        mc.world?.let { world ->
            val pos = info.context.expectedPos
            val loaded = world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.x), ChunkSectionPos.getSectionCoord(pos.z))
            if (!loaded) return@let

            info("${info::class.simpleName} at ${info.context.expectedPos.toShortString()} timed out")

            val awaitThenBreak = info.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak
            if (!info.broken && awaitThenBreak) {
                world.setBlockState(info.context.expectedPos, info.context.checkedState)
            }
        }
        info.pendingInteractions.remove(info.context)
    }

    init {
        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE + 1) { event ->
            run {
                pendingBreaks.firstOrNull { it.context.expectedPos == event.pos }
                    ?: if (reBreak?.context?.expectedPos == event.pos) reBreak
                    else null
            }?.let { pending ->
                // return if the state hasn't changed
                if (event.newState.matches(pending.context.checkedState))
                    return@listen

                // return if the block's not broken
                if (!isBroken(pending.context.checkedState, event.newState)) {
                    if (!pending.isReBreaking) {
                        this@BrokenBlockHandler.warn("Broken block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.checkedState.brokenState}")
                    }
                    pending.stopPending()
                    return@listen
                }

                if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak) {
                    destroyBlock(pending)
                }
                pending.internalOnBreak()
                if (pending.callbacksCompleted) {
                    pending.stopPending()
                    if (lastPosStarted == pending.context.expectedPos) {
                        ReBreakManager.startReBreak(pending)
                    }
                }
                return@listen
            }
        }

        listen<EntityEvent.Update>(priority = Int.MIN_VALUE + 1) {
            if (it.entity !is ItemEntity) return@listen
            run {
                pendingBreaks.firstOrNull { info -> matchesBlockItem(info, it.entity) }
                    ?: reBreak?.let { info ->
                        return@run if (matchesBlockItem(info, it.entity)) info
                        else null
                    }
            }?.let { pending ->
                pending.internalOnItemDrop(it.entity)
                if (pending.callbacksCompleted) {
                    pending.stopPending()
                    if (lastPosStarted == pending.context.expectedPos) {
                        ReBreakManager.startReBreak(pending)
                    }
                }
                return@listen
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE + 1) {
            pendingBreaks.clear()
        }
    }

    /**
     * Adds the [info] to the [BrokenBlockHandler], and requesters, pending interaction collections.
     */
    fun BreakInfo.startPending() {
        pendingBreaks.add(this)
        pendingInteractions.add(context)
    }

    /**
     * Removes the [info] from the [BrokenBlockHandler], and requesters, pending interaction collections.
     */
    fun BreakInfo.stopPending() {
        if (!isReBreaking) {
            pendingBreaks.remove(this)
            pendingInteractions.remove(context)
        } else {
            resetCallbacks()
        }
    }

    /**
     * Sets the size limit and decay time for the [pendingBreaks] [LimitedDecayQueue]
     * using the [request]'s configs
     */
    fun setPendingConfigs(request: BreakRequest) {
        pendingBreaks.setSizeLimit(request.build.breaking.maxPendingBreaks)
        pendingBreaks.setDecayTime(request.build.interactionTimeout * 50L)
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

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, gamemode)) return false

        if (!player.mainHandStack.item.canMine(ctx.checkedState, world, ctx.expectedPos, player))
            return false
        val block = ctx.checkedState.block
        if (block is OperatorBlock && !player.isCreativeLevelTwoOp) return false
        if (ctx.checkedState.isAir) return false

        block.onBreak(world, ctx.expectedPos, ctx.checkedState, player)
        val fluidState = fluidState(ctx.expectedPos)
        val setState = world.setBlockState(ctx.expectedPos, fluidState.blockState, 11)
        if (setState) block.onBroken(world, ctx.expectedPos, ctx.checkedState)

        if (info.breakConfig.breakingTexture) info.setBreakingTextureStage(player, world, -1)

        return setState
    }

    val BlockState.isEmpty get() = matches(fluidState.blockState)
    val BlockState.brokenState: BlockState get() = fluidState.blockState
    fun isBroken(oldState: BlockState, newState: BlockState) = !oldState.isEmpty && oldState.brokenState.matches(newState)
}