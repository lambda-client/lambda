/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.handlers.breaking

import com.lambda.config.automation.AutomationConfig.Companion.DEFAULT
import com.lambda.config.blocks.BreakConfig.BreakConfirmationMode
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.simulation.processing.ProcessorRegistry
import com.lambda.interaction.managers.PostActionHandler
import com.lambda.interaction.managers.breaking.BreakManager.lastPosStarted
import com.lambda.interaction.managers.breaking.BreakManager.matchesBlockItem
import com.lambda.interaction.handlers.breaking.RebreakHandler.rebreak
import com.lambda.interaction.managers.breaking.BreakInfo
import com.lambda.module.modules.client.Client
import com.lambda.module.modules.client.Client.verboseDebug
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.emptyState
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotBroken
import com.lambda.util.BlockUtils.matches
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.PlayerUtils.gamemode
import net.minecraft.block.OperatorBlock
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.ChunkSectionPos

/**
 * Designed to handle blocks that are deemed broken, yet are awaiting
 * confirmation from the server and/or an item drop.
 *
 * @see com.lambda.interaction.managers.breaking.BreakManager
 */
object BrokenBlockHandler : PostActionHandler<BreakInfo>() {
	override val pendingActions = LimitedDecayQueue<BreakInfo>(
		DEFAULT.buildConfig.maxPendingActions, DEFAULT.buildConfig.actionTimeout * 50L
	) { info ->
		runSafe {
			val pos = info.context.blockPos
			val loaded =
				world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.x), ChunkSectionPos.getSectionCoord(pos.z))
			if (!loaded) return@runSafe

			if (!info.broken) {
				val message = "${info.type} ${info::class.simpleName} at ${info.context.blockPos.toShortString()} timed out with cached state ${info.context.cachedState}"
				if (verboseDebug) this@BrokenBlockHandler.warn(message)
			} else if (!Client.ignoreItemDropWarnings) {
				val message = "${info.type} ${info::class.simpleName}'s item drop at ${info.context.blockPos.toShortString()} timed out"
				if (verboseDebug) this@BrokenBlockHandler.warn(message)
			}

			if (!info.broken && info.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak) {
				world.setBlockState(info.context.blockPos, info.context.cachedState)
			}

			info.request.onCancel?.invoke(this, info.context.blockPos)
		}
		info.pendingInteractionsList.remove(info.context)
	}

    init {
        listen<WorldEvent.BlockUpdate.Server>({ Int.MIN_VALUE }) { event ->
            run {
                pendingActions.firstOrNull { it.context.blockPos == event.pos }
                    ?: if (rebreak?.context?.blockPos == event.pos) rebreak
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

					if (pending.type == BreakInfo.BreakType.Rebreak) {
						pending.context.cachedState = event.newState
					} else {
						val message = "Broken block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.cachedState.emptyState}"
						if (verboseDebug) this@BrokenBlockHandler.warn(message)
						pending.stopPending()
					}
					return@listen
				}

				if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak
					|| (pending.type == BreakInfo.BreakType.Rebreak && !pending.breakConfig.rebreak)
					) {
					destroyBlock(pending)
				}
				pending.internalOnBreak()
				if (pending.callbacksCompleted) {
					pending.stopPending()
					if (lastPosStarted == pending.context.blockPos) {
						RebreakHandler.offerRebreak(pending)
					}
				}
				return@listen
			}
		}

        listen<EntityEvent.Update>({ Int.MIN_VALUE }) {
			runGameScheduled {
				val entity = it.entity
				if (entity !is ItemEntity) return@runGameScheduled
				val pending =
					pendingActions.firstOrNull { info -> matchesBlockItem(info, entity) }
						?: rebreak?.let { info ->
							if (matchesBlockItem(info, entity)) info
							else return@runGameScheduled
						} ?: return@runGameScheduled

				pending.internalOnItemDrop(entity)
				if (pending.callbacksCompleted) {
					pending.stopPending()
					if (lastPosStarted == pending.context.blockPos) {
						RebreakHandler.offerRebreak(pending)
					}
				}
			}
		}
	}

	/**
	 * A modified version of the minecraft breakBlock method.
	 *
	 * Performs the actions required to display breaking particles, sounds, texture overlay, etc.
	 * based on the user's settings.
	 *
	 * @see net.minecraft.client.world.ClientWorld.breakBlock
	 */
	fun SafeContext.destroyBlock(info: BreakInfo) {
		val ctx = info.context

		if (player.isBlockBreakingRestricted(world, ctx.blockPos, gamemode)) return
		if (!player.mainHandStack.canMine(ctx.cachedState, world, ctx.blockPos, player)) return

		val block = ctx.cachedState.block
		if (block is OperatorBlock && !player.isCreativeLevelTwoOp) return
		if (ctx.cachedState.isEmpty) return

		block.onBreak(world, ctx.blockPos, ctx.cachedState, player)
		val fluidState = fluidState(ctx.blockPos)
		val setState = world.setBlockState(ctx.blockPos, fluidState.blockState, 11)
		if (setState) block.onBroken(world, ctx.blockPos, ctx.cachedState)

		if (info.breakConfig.breakingTexture) info.setBreakingTextureStage(-1)
	}
}
