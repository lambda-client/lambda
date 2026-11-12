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

package com.lambda.interaction.handler.handlers.breaking

import com.lambda.config.automation.AutomationConfig.Companion.DEFAULT
import com.lambda.config.blocks.BreakConfig.BreakConfirmationMode
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.simulation.processing.ProcessorRegistry
import com.lambda.interaction.handler.PostActionHandler
import com.lambda.interaction.manager.Request.Companion.invoke
import com.lambda.interaction.manager.managers.breaking.BreakInfo
import com.lambda.interaction.manager.managers.breaking.BreakManager.matchesBlockItem
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
import java.util.Collections
import java.util.WeakHashMap

/**
 * Designed to handle blocks that are deemed broken, yet are awaiting
 * confirmation from the server and/or an item drop.
 *
 * @see com.lambda.interaction.manager.managers.breaking.BreakManager
 */
internal object BrokenBlockHandler : PostActionHandler<BreakInfo>() {
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
			} else if (!info.validBreak) {
				val message = "${info.type} ${info::class.simpleName}'s item drop at ${info.context.blockPos.toShortString()} timed out"
				if (verboseDebug) this@BrokenBlockHandler.warn(message)
			}

			if (!info.broken && info.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak) {
				world.setBlockState(info.context.blockPos, info.context.cachedState)
			}

			if (!info.validBreak) {
				info.request.onCancels.invoke(info.context.blockPos)
				info.request.onRejects.invoke(info.context.blockPos)
			}
		}
		info.pendingInteractionsList.remove(info.context)
	}

	private val reportedDrops = Collections.newSetFromMap(WeakHashMap<ItemEntity, Boolean>())

    init {
        listen<WorldEvent.BlockUpdate.Server>({ Int.MIN_VALUE }) { event ->
            pendingActions.firstOrNull { !it.broken && it.context.blockPos == event.pos }?.let { pending ->
                val currentState = pending.context.cachedState
                // return if the block's not broken
                if (isNotBroken(currentState, event.newState)) {
                    // return if the state hasn't changed
                    if (event.newState.matches(currentState, ProcessorRegistry.postProcessedProperties)) {
                        pending.context.cachedState = event.newState
                        return@listen
                    }

					val message = "Broken block at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${pending.context.cachedState.emptyState}"
					if (verboseDebug) this@BrokenBlockHandler.warn(message)
					pending.stopPending()
					pending.request.onCancels.invoke(event.pos)
					pending.request.onRejects.invoke(event.pos)
					return@listen
				}

				if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak) destroyBlock(pending)
				pendingActions.refresh(pending)
				pending.internalOnBreak()
				RebreakHandler.offerRebreak(pending)
				if (pending.callbacksCompleted) pending.stopPending()
				return@listen
			}
		}

        listen<EntityEvent.Update>({ Int.MIN_VALUE }) {
			runGameScheduled {
				val entity = it.entity
				if (entity !is ItemEntity) return@runGameScheduled
				if (entity in reportedDrops) return@runGameScheduled
				val matches = pendingActions.filter { info -> matchesBlockItem(info, entity) }
				val pending = matches.firstOrNull { info -> info.drops.isEmpty() }
					?: matches.firstOrNull()
					?: return@runGameScheduled

				reportedDrops += entity
				pending.internalOnItemDrop(entity)
				if (pending.callbacksCompleted) pending.stopPending()
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
