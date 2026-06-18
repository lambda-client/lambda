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

package com.lambda.module.modules.movement.elytrafly

import baritone.api.pathing.goals.GoalGetToBlock
import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.context.SafeContext
import com.lambda.interaction.BaritoneHandler
import com.lambda.module.hud.Speedometer
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.threading.runGameScheduled
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.SpeedUnit
import com.lambda.util.math.dist
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.isLoaded
import com.lambda.util.world.raycast.InteractionMask
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.rayCast
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
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
	var passingToPos: Vec3d? = null

	init {
		onEnable { startPos = player.pos }
		onDisable {
			passingToPos = null
			if (passerConfig.passObstacles) BaritoneHandler.cancel()
		}
		onFlag {
			if (!passerConfig.passObstacles || !passerConfig.walkWhenFlagged) return@onFlag
			val snappedDir = getSnappedDir()
			val closestLinePoint = player.pos.findClosestPointOnLine(snappedDir)
			runGameScheduled {
				val delta = snappedDir.multiply(passerConfig.obstacleLookAhead.toDouble())
				val pathToPoint = closestLinePoint.add(delta)
				pathToValidPoint(pathToPoint, snappedDir)
			}
		}
	}

	fun SafeContext.handlePassingObstacles(): Boolean {
		if (!BaritoneHandler.baritoneAvailable) {
			logError("Obstacle passing requires baritone to be installed!")
			ElytraFly.disable()
			return true
		}

		if (!BaritoneHandler.isActive) passingToPos = null

		if (!passerConfig.passObstacles) return false

		val playerPos = player.pos
		val validDistanceFromStart = Vec3d(playerPos.x, startPos.y, playerPos.z) dist startPos > 0.1
		if (!validDistanceFromStart) return false

		val snappedDir = getSnappedDir()
		val closestLinePoint = playerPos.findClosestPointOnLine(snappedDir)

		passingToPos?.let { passingTo ->
			if (passingTo.isObstructed(snappedDir)) {
				pathToValidPoint(passingTo, snappedDir)
			}
			return true
		}

		if (!player.isOnGround) return false

		val notProgressing = Speedometer.calculateSpeed(true, SpeedUnit.BlocksPerSecond) < 0.01
		if (player.isGliding && notProgressing) {
			pathToValidPoint(closestLinePoint, snappedDir)
			return true
		}

		val xy = Vec3d(playerPos.x, closestLinePoint.y, playerPos.z)

		// We only want to account for horizontal and below the line rather than total
		// distance as jumping from bounce might cause false positives
		val distanceToLine = xy
			.dist(closestLinePoint) + (playerPos.y - closestLinePoint.y)
			.coerceAtMost(0.0)
		if (distanceToLine > passerConfig.acceptableOffsetRange) {
			pathToValidPoint(closestLinePoint, snappedDir, true)
			return true
		}

		val isObstructed = xy.isObstructed(snappedDir)
		if (isObstructed) {
			pathToValidPoint(closestLinePoint, snappedDir)
			return true
		}

		if (BaritoneHandler.isActive) return true

		return false
	}

	private fun SafeContext.getSnappedDir(): Vec3d {
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

	context(safeContext: SafeContext)
	private fun pathToValidPoint(startSearchPos: Vec3d, dir: Vec3d, initialBlockedCheck: Boolean = false) {
		var skippingFirstCheck = !initialBlockedCheck
		var searchPos = startSearchPos
		while (skippingFirstCheck || searchPos.isObstructed(dir)) {
			searchPos = searchPos.add(dir.multiply(passerConfig.obstacleLookAhead.toDouble()))
			skippingFirstCheck = false
		}
		passTo(searchPos)
		safeContext.player.stopGliding()
	}

	private fun passTo(pos: Vec3d) {
		passingToPos = pos
		BaritoneHandler.setGoalAndPath(GoalGetToBlock(pos.flooredBlockPos))
	}

	private fun Vec3d.findClosestPointOnLine(snappedDirection: Vec3d): Vec3d {
		val startToCurrent = subtract(startPos)
		val t = startToCurrent.dotProduct(snappedDirection) / snappedDirection.dotProduct(snappedDirection)
		return startPos.add(snappedDirection.multiply(t))
	}

	context(safeContext: SafeContext)
	private fun Vec3d.isObstructed(direction: Vec3d) =
		if (!isLoaded) false
		else {
			flooredBlockPos.down().let { downPos ->
				!safeContext.blockState(downPos).isSolidBlock(safeContext.world, downPos)
			} ||
					add(0.0, passerConfig.minObstacleHeight, 0.0).rayCastObstructed(direction) ||
					add(0.0, 1.01, 0.0).rayCastObstructed(direction) ||
					add(0.0, 1.99, 0.0).rayCastObstructed(direction)
		}

	context(safeContext: SafeContext)
	private fun Vec3d.rayCastObstructed(direction: Vec3d) =
		safeContext.rayCast(
			this,
			direction,
			passerConfig.obstacleLookAhead.toDouble(),
			InteractionMask.Block,
			RaycastContext.ShapeType.COLLIDER
		)?.blockResult != null
}

class PasserSettings(override val c: Config) : ConfigBlock {
	val passObstacles by c.setting("Pass Obstacles", true, "Automatically paths around obstacles using baritone")
	val walkWhenFlagged by c.setting("Walk When Flagged", true, "Triggers obstacle passer when the server forces your position (typically getting flagged by the anticheat)") { passObstacles }
	val minObstacleHeight by c.setting("Min Obstacle Height", 0.063, 0.0..1.0, 0.0001, "The minimum height an obstacle must be above the ground to trigger obstacle passer") { passObstacles }
	val acceptableOffsetRange by c.setting("Acceptable Offset Range", 2.0, 0.1..5.0, 0.01, "Acceptable offset from the original flight line to allow when starting to fly again after passing obstacles") { passObstacles }
	val obstacleLookAhead by c.setting("Obstacle Look-Ahead", 15, 0..50, 1, "Looks ahead of the player to see if obstacles are in the way") { passObstacles }
	val directionStep by c.setting("Direction Step", 45.0, 0.0..180.0, 0.1, "The step size to use when locking the flight direction") { passObstacles }
}