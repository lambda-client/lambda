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

import com.lambda.context.SafeContext
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.util.world.blockSearch
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos

@Ta5kBuilder
fun findBlock(block: Block, searchRadius: Int) = FindPlacedBlockTask(block, searchRadius)

class FindPlacedBlockTask(
	private val block: Block,
	private val searchRadius: Int
) : Task<BlockPos>() {
	override val name = "Finding placed block: $block"

	override fun SafeContext.onStart() {
		val pos =
			blockSearch(searchRadius, player.blockPos) { _, state ->
				state.isOf(block)
			}.keys.firstOrNull()

		if (pos != null) success(pos)
		else failure(IllegalStateException("Could not find block $block"))
	}
}