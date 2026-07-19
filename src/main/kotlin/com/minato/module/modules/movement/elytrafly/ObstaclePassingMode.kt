
package com.minato.module.modules.movement.elytrafly

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.context.SafeContext
import com.minato.module.modules.movement.elytrafly.ElytraFly.FlyMode
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

abstract class ObstaclePassingMode(
	override val c: Config,
	flyMode: FlyMode
) : ElytraFlyMode(flyMode) {
	abstract val passerConfig: PasserSettings

	var startPos: Vec3d = Vec3d.ZERO

	protected fun SafeContext.getSnappedDir(): Vec3d {
		val travelDiff = player.pos.subtract(startPos).normalize().let { Vec3d(it.x, 0.0, it.z) }
		return lockYawToStep(travelDiff)
	}

	private fun lockYawToStep(vector: Vec3d): Vec3d {
		val yaw = atan2(vector.z, vector.x)
		val yawDegrees = Math.toDegrees(yaw)

		val normalizedYaw = (yawDegrees % 360.0 + 360.0) % 360.0

		val steps = normalizedYaw / passerConfig.directionStep
		val roundedSteps = steps.roundToInt()
		val lockedYawDegrees = roundedSteps * passerConfig.directionStep

		val normalizedLockedYawDegrees = (lockedYawDegrees % 360.0 + 360.0) % 360.0
		val lockedYaw = Math.toRadians(normalizedLockedYawDegrees)

		val horizontalLength = hypot(vector.x, vector.z)
		val x = cos(lockedYaw) * horizontalLength
		val z = sin(lockedYaw) * horizontalLength

		return Vec3d(x, vector.y, z)
	}

	protected fun Vec3d.findClosestPointOnLine(snappedDirection: Vec3d): Vec3d {
		val startToCurrent = subtract(startPos)
		val t = startToCurrent.dotProduct(snappedDirection) / snappedDirection.dotProduct(snappedDirection)
		return startPos.add(snappedDirection.multiply(t))
	}

	fun handlePassingObstacles(): Boolean = false
}

class PasserSettings(override val c: Config) : ConfigBlock {
	val passObstacles by c.setting("Pass Obstacles", true, "Automatically pass around obstacles")
	val walkWhenFlagged by c.setting("Walk When Flagged", true, "Triggers obstacle passer when the server forces your position (typically getting flagged by the anticheat)") { passObstacles }
	val minObstacleHeight by c.setting("Min Obstacle Height", 0.063, 0.0..1.0, 0.0001, "The minimum height an obstacle must be above the ground to trigger obstacle passer") { passObstacles }
	val headHitters by c.setting("Head Hitters", true, "Flags obstacles above the y level you started flying at") { passObstacles }
	val acceptableOffsetRange by c.setting("Acceptable Offset Range", 2.0, 0.1..5.0, 0.01, "Acceptable offset from the original flight line to allow when starting to fly again after passing obstacles") { passObstacles }
	val obstacleLookAhead by c.setting("Obstacle Look-Ahead", 8, 0..50, 1, "Looks ahead of the player to see if obstacles are in the way") { passObstacles }
	val directionStep by c.setting("Direction Step", 22.5, 0.0..180.0, 0.1, "The step size to use when locking the flight direction") { passObstacles }
}
