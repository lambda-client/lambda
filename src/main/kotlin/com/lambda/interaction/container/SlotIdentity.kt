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

package com.lambda.interaction.container

import com.lambda.context.SafeContext
import com.lambda.interaction.handler.handlers.ContainerHandler
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

sealed interface SlotIdentity {
	data class Player(val index: Int) : SlotIdentity
	data class Placed(val positions: Set<BlockPos>, val index: Int) : SlotIdentity
	data class Ender(val index: Int) : SlotIdentity

	context(safeContext: SafeContext)
	fun resolve(): Slot? {
		val handler = safeContext.player.currentScreenHandler
		val (key, ender) = currentContainerAnchor()
		return resolveIn(handler.slots, key, ender)
	}

	fun resolveIn(
		slots: List<Slot>,
		containerKey: Set<BlockPos>?,
		enderOpen: Boolean
	): Slot? =
		when (this) {
			is Player ->
				slots.firstOrNull { it.inventory is PlayerInventory && it.index == index }
			is Placed ->
				if (containerKey != null && containerKey == positions)
					slots.firstOrNull { it.inventory is SimpleInventory && it.index == index }
				else null
			is Ender ->
				if (enderOpen)
					slots.firstOrNull { it.inventory is SimpleInventory && it.index == index }
				else null
		}

	context(safeContext: SafeContext)
	fun holds(stack: ItemStack): Boolean {
		val live = resolve()?.stack ?: return false
		if (live.isEmpty || stack.isEmpty) return false
		return live.item == stack.item && live.name.string == stack.name.string
	}
}

fun Slot.identity(): SlotIdentity? {
	if (inventory is PlayerInventory) return SlotIdentity.Player(index)
	if (inventory !is SimpleInventory) return null
	val (key, ender) = currentContainerAnchor()
	if (key != null) return SlotIdentity.Placed(key, index)
	if (ender) return SlotIdentity.Ender(index)
	return null
}

fun currentContainerAnchor(): Pair<Set<BlockPos>?, Boolean> =
	ContainerHandler.accessedPlacedContainer?.positions to
			(ContainerHandler.lastInteractedBlockEntity is EnderChestBlockEntity)
