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
import com.lambda.interaction.handler.handlers.findSlots
import com.lambda.interaction.inventory.select
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.item
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos

@Ta5kBuilder
context(automated: Automated)
fun acquirePlacedBlock(
	block: Block,
	maxSearchRadius: Int = 10
) = AcquirePlacedBlockTask(block, maxSearchRadius, automated)

class AcquirePlacedBlockTask @Ta5kBuilder internal constructor(
	val block: Block,
	val searchRadius: Int = 10,
	automated: Automated
) : Task<BlockPos>(), Automated by automated {
	override val name get() = "Acquiring placed ${block.name.string}"

	init {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				block.item
					.select(1)
					.findSlots()
					.firstOrNull()?.let { slot ->
						PlaceContainerTask(slot, this)
							.onSuccess { success(it) }
							.execute(this@AcquirePlacedBlockTask)
						return@listen
					}
			}

			failure("No $block found in range or hotbar/inventory!")
			return@listen
		}
	}

	override fun SafeContext.onStart() {
		findBlock(block, searchRadius)
			.softFail()
			.execute(this@AcquirePlacedBlockTask)
	}
}