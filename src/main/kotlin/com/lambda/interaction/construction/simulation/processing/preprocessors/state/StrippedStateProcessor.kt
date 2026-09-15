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
import com.lambda.util.item.ItemUtils
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AxeItem
import net.minecraft.item.Item
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object StrippedStateProcessor : StateProcessor {
	// [AxeItem.STRIPPED_BLOCKS] maps unstripped to stripped, we need the other direction
	private val unstrippedToStripped: Map<Block, Block> by lazy {
		AxeItem.STRIPPED_BLOCKS.entries.associate { (from, to) -> to to from }
	}

	override fun acceptsState(state: BlockState, targetState: BlockState): Boolean {
		val unstrippedVariant = unstrippedToStripped[targetState.block] ?: return false
		return state.isReplaceable || state.block == unstrippedVariant
	}

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		val unstrippedVariant = unstrippedToStripped[targetState.block] ?: return
		val player = safeContext.player

		// Every branch below depends on the current inventory, so this must never be cached.
		noCaching()

		if (state.isReplaceable){
			val sourceItem = unstrippedVariant.asItem()
			if (!player.carries(sourceItem)) return

			setExpectedState(unstrippedVariant.withAxisOf(targetState))
			setItem(sourceItem)
		} else if (state.block == unstrippedVariant) {
			val axe = player.findAxe() ?: return
			setItem(axe)
			setPlacing(false)
			// InteractSim refuses to interact while sneaking, so ask for it explicitly.
			setSneak(false)
			return
		}
	}

	private fun Block.withAxisOf(targetState: BlockState) = defaultState.let { placed ->
		if (Properties.AXIS in placed && Properties.AXIS in targetState) {
			placed.with(Properties.AXIS, targetState.get(Properties.AXIS))
		} else placed
	}

	private fun PlayerEntity.findAxe(): Item? {
		val held = mainHandStack.item
		if (held in ItemUtils.axes) return held
		return inventory.mainStacks.firstOrNull { it.item in ItemUtils.axes }?.item
	}

	private fun PlayerEntity.carries(item: Item) =
		inventory.mainStacks.any { it.item == item }
}
