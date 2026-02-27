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

package com.lambda.module.modules.movement

import baritone.api.pathing.goals.GoalXZ
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockPosIterators
import com.lambda.util.extension.isNether
import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

@Suppress("unused")
object AutoSpiral : Module(
	name = "AutoSpiral",
	description = "Automatically flies in a spiral pattern. Uses Baritone elytra pathing in the Nether.",
	tag = ModuleTag.MOVEMENT,
) {
	var iterator: BlockPosIterators.SpiralIterator2d? = null
	var currentWaypoint: BlockPos? = null
	var center: BlockPos = BlockPos.ORIGIN

	var spiralSpacing by setting("Spiral Spacing", 128, 16..1024, description = "The distance between each loop of the spiral")
	var waypointTriggerDistance by setting("Waypoint Trigger Distance", 4, 2..64, description = "The distance to the waypoint at which a new waypoint is generated. Put in 50-60 range when in the Nether.")
	var setCenterOnEnable by setting("Set Center On Enable", true, description = "Whether to set the center of the spiral to your current position when enabling the module.")
	var setBaritoneGoal by setting("Set Baritone Goal", true, description = "Whether to set Baritone's goal to the current waypoint. Mostly so you can see where the next waypoint is.")

	init {
		button("Reset Center") {
			runSafe {
				center = player.blockPos
				currentWaypoint = null
			}
		}
		button("Next Waypoint") {
			runSafe {
				currentWaypoint = null
			}
		}

		onEnable {
			if (iterator == null) {
				iterator = BlockPosIterators.SpiralIterator2d(10000)
				if (setCenterOnEnable) center = player.blockPos
			}
		}

		onDisable {
			iterator = null
			currentWaypoint = null
			BaritoneManager.cancel()
		}

		listen<TickEvent.Pre> {
			if (currentWaypoint == null || waypointReached()) {
				nextWaypoint()
			}

			currentWaypoint?.let { waypoint ->
				if (!world.isNether) {
					rotationRequest {
						lookAt(waypoint.toCenterPos()).yaw
					}.submit(true)
				}
			}
		}
	}

	private fun SafeContext.waypointReached(): Boolean {
		return currentWaypoint?.let {
			val distance = distanceXZ(player.blockPos, it)
			return distance <= waypointTriggerDistance
		} ?: false
	}

	private fun distanceXZ(a: BlockPos, b: BlockPos): Double {
		val dx = (a.x - b.x).toDouble()
		val dz = (a.z - b.z).toDouble()
		return sqrt(dx * dx + dz * dz)
	}

	private fun SafeContext.nextWaypoint() {
		iterator?.next()?.let { pos ->
			val scaled = pos.multiply(spiralSpacing)
			val w = scaled.add(center)
			if (world.isNether) {
				BaritoneManager.setGoalAndElytraPath(GoalXZ(w.x, w.z))
			} else {
				if (setBaritoneGoal) BaritoneManager.setGoal(GoalXZ(w.x, w.z))
			}
			currentWaypoint = w
		}
	}
}