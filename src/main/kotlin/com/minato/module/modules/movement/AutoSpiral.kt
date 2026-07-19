
package com.minato.module.modules.movement

import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.BlockPosIterators
import com.minato.util.extension.isNether
import com.minato.util.player.RotationUtils.lookAt
import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

@Suppress("unused")
object AutoSpiral : Module(
	name = "AutoSpiral",
	description = "Automatically flies in a spiral pattern.",
	tag = ModuleTag.MOVEMENT,
) {
	var iterator: BlockPosIterators.SpiralIterator2d? = null
	var currentWaypoint: BlockPos? = null

	var spiralSpacing by setting("Spiral Spacing", 128, 16..1024, description = "The distance between each loop of the spiral")
	var waypointTriggerDistance by setting("Waypoint Trigger Distance", 4, 2..64, description = "The distance to the waypoint at which a new waypoint is generated. Put in 50-60 range when in the Nether.")
	var setCenterOnEnable by setting("Set Center On Enable", true, description = "Whether to set the center of the spiral to your current position when enabling the module.")

	var center by setting("Center", BlockPos.ORIGIN, description = "Center position for the spiral")

	init {
		onEnable {
			if (iterator == null) {
				iterator = BlockPosIterators.SpiralIterator2d(10000)
				if (setCenterOnEnable) {
					center = player.blockPos
				}
			}
		}

		onDisable {
			iterator = null
			currentWaypoint = null
		}

		listen<TickEvent.Pre> {
			if (currentWaypoint == null || waypointReached()) {
				nextWaypoint()
			}

			currentWaypoint?.let { waypoint ->
				if (!world.isNether) {
					rotationRequest {
						yaw(lookAt(waypoint.toCenterPos()).yaw)
					}.submit(true)
				}
			}
		}
	}

	private fun SafeContext.waypointReached() =
		currentWaypoint?.let {
			val distance = distanceXZ(player.blockPos, it)
			distance <= waypointTriggerDistance
		} ?: false


	private fun distanceXZ(a: BlockPos, b: BlockPos): Double {
		val dx = (a.x - b.x).toDouble()
		val dz = (a.z - b.z).toDouble()
		return sqrt(dx * dx + dz * dz)
	}

	private fun SafeContext.nextWaypoint() {
		iterator?.next()?.let { pos ->
			val scaled = pos.multiply(spiralSpacing)
			val w = scaled.add(center)
			currentWaypoint = w
		}
	}
}