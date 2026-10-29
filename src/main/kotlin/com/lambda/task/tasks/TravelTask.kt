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

import baritone.api.pathing.goals.GoalBlock
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import net.minecraft.util.math.BlockPos

@Ta5kBuilder
fun travelTo(blockPos: BlockPos) = TravelTask(blockPos)

class TravelTask @Ta5kBuilder internal constructor(
	val toPos: BlockPos
) : Task<BlockPos>() {
	override val name = "Travelling to $toPos"

	private var idleTicks = 0

	override fun SafeContext.onStart() {
		if (checkFinish()) return
		BaritoneHandler.setGoalAndPath(GoalBlock(toPos))
	}

	init {
		onCompletion { BaritoneHandler.cancel() }

		listen<TickEvent.Pre> {
			if (checkFinish()) return@listen

			idleTicks =
				if (BaritoneHandler.isActive) 0
				else idleTicks + 1

			if (idleTicks >= RE_PATH_TICKS) {
				idleTicks = 0
				BaritoneHandler.setGoalAndPath(GoalBlock(toPos))
			}
		}
	}

	private fun SafeContext.checkFinish(): Boolean =
		if (player.blockPos == toPos) {
			success(toPos)
			true
		} else false

	companion object {
		const val RE_PATH_TICKS = 20
	}
}