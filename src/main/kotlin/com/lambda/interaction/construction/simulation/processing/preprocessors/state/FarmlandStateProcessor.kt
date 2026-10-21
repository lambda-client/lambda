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

package com.lambda.interaction.construction.simulation.processing.preprocessors.state

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.StateProcessor
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemUtils
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object FarmlandStateProcessor : StateProcessor {
	private val tillResults = mapOf(
		Blocks.GRASS_BLOCK to Blocks.FARMLAND,
		Blocks.DIRT to Blocks.FARMLAND,
		Blocks.DIRT_PATH to Blocks.FARMLAND,
		Blocks.COARSE_DIRT to Blocks.DIRT,
		Blocks.ROOTED_DIRT to Blocks.DIRT
	)

	private val sourceBlocks = listOf(Blocks.DIRT, Blocks.GRASS_BLOCK)

	override fun acceptsState(state: BlockState, targetState: BlockState) =
		targetState.block == Blocks.FARMLAND && state.block != targetState.block &&
				(state.isReplaceable || state.block in tillResults)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		val player = safeContext.player
		noCaching()

		if (state.isReplaceable) {
			if (player.carries(Items.FARMLAND)) return
			val sourceBlock = sourceBlocks.firstOrNull { player.carries(it.asItem()) } ?: Blocks.DIRT
			setExpectedState(sourceBlock.defaultState)
			setItem(sourceBlock.asItem())
			return
		}

		val tilledBlock = tillResults[state.block] ?: return
		if (!safeContext.blockState(pos.up()).isAir) {
			omitInteraction()
			return
		}
		val hoe = player.findHoe() ?: Items.NETHERITE_HOE

		setExpectedState(tilledBlock.defaultState)
		setItem(hoe)
		setPlacing(false)
		retainSides { it != Direction.DOWN }
		setSneak(false)
	}

	private fun ClientPlayerEntity.findHoe(): Item? =
		mainHandStack.item.takeIf { it in ItemUtils.hoes }
			?: hotbarAndInventoryStacks.firstOrNull { it.item in ItemUtils.hoes }?.item

	private fun ClientPlayerEntity.carries(item: Item) =
		hotbarAndInventoryStacks.any { it.item == item }
}
