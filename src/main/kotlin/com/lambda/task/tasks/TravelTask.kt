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

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.task.Task
import com.lambda.task.TaskMarker
import net.minecraft.util.math.BlockPos

@TaskMarker
fun travelTo(blockPos: BlockPos, range: Int = 0) = TravelTask(blockPos, range)

class TravelTask @TaskMarker internal constructor(
	val toPos: BlockPos,
	private val range: Int = 0
) : Task<BlockPos>() {
	override val name = "Travelling to $toPos"

	private val goal: Goal
		get() =
			if (range > 0) GoalNear(toPos, range)
			else GoalBlock(toPos)

	private var idleTicks = 0
	private var stuckTicks = 0
	private var lastPos: BlockPos? = null

	override fun SafeContext.onStart() {
		if (checkFinish()) return
		BaritoneHandler.setGoalAndPath(goal)
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
				BaritoneHandler.setGoalAndPath(goal)
			}

			stuckTicks =
				if (player.blockPos == lastPos) stuckTicks + 1
				else 0
			lastPos = player.blockPos
			if (stuckTicks >= STUCK_TICKS) failure("Couldn't get to ${toPos.toShortString()}")
		}
	}

	private fun SafeContext.checkFinish(): Boolean =
		if (player.blockPos.getSquaredDistance(toPos) <= range * range) {
			success(toPos)
			true
		} else false

	companion object {
		const val RE_PATH_TICKS = 20
		const val STUCK_TICKS = 100
	}
}