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

package com.lambda.task.tasks

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handlers.ContainerHandler.findSlotsWithMaterial
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.select
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.Block
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

class AcquirePlacedBlockTask @Ta5kBuilder constructor(
	val block: Block,
	val maxSearchRadius: Int = 10,
	automated: Automated
) : Task<BlockPos>(), Automated by automated {
	override val name get() = "Acquiring placed ${block.name.string}"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				Items.ENDER_CHEST
					.select(1)
					.findSlotsWithMaterial()
					.firstOrNull()?.let { slot ->
						PlaceContainerTask(slot, this).finally {
							success(it)
						}.execute(this@AcquirePlacedBlockTask)
						return@listen
					}
			}

			failure("No ender chest found in range or hotbar/inventory!")
			return@listen
		}
	}

	override fun SafeContext.onStart() {
		checkInRange()?.let { success(it) }
	}

	private fun SafeContext.checkInRange() =
		BlockPos.iterateOutwards(
			player.blockPos,
			maxSearchRadius,
			maxSearchRadius,
			maxSearchRadius
		).map { it.blockPos }
			.firstOrNull { blockPos ->
				blockState(blockPos).block == block
			}
}