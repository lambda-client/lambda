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

package com.lambda.module.modules.movement.elytrafly.modes

import com.lambda.config.Config
import com.lambda.config.Group
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneHandler
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.module.hud.Speedometer
import com.lambda.module.modules.movement.BetterFirework.canOpenElytra
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ElytraFly.canTakeoff
import com.lambda.module.modules.movement.elytrafly.ElytraFly.mode
import com.lambda.module.modules.movement.elytrafly.ObstaclePassingMode
import com.lambda.module.modules.movement.elytrafly.PasserSettings
import com.lambda.threading.runSafe
import com.lambda.util.SpeedUnit
import com.lambda.util.TickTimer
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

class BounceElytraFly(
	override val c: Config
) : ObstaclePassingMode(c, FlyMode.Bounce) {
	companion object {
		private const val Y_MOTION_GROUP = "Y Motion"
		private const val BOUNCE_OBSTACLE_PASSER_GROUP = "Bounce Obstacle Passer"
	}

	private val takeoff by c.setting("Takeoff", true, "Automatically jumps and initiates gliding")
	private val autoPitch by c.setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds")
	private val pitch by c.setting("Pitch", 80.0, -90.0..90.0, 0.000001) { autoPitch }
	private val jump by c.setting("Jump", true, "Automatically jumps")
	private val flagPause by c.setting("FlagPause Pause", 5, 0..100, 1, "How long to pause if the server flags you for a movement check", "ticks")

	@Group(Y_MOTION_GROUP) val yMotionSetting by c.setting("Y Motion", false, "Cancels the players y velocity to aid speed")
	@Group(Y_MOTION_GROUP) val onlyOnDiagonal: Boolean by c.setting("Only On Diagonal", true, "Only use y motion when the player is flying on a non-axial angle") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val minDiagonalAngle by c.setting("Min Diagonal Angle", 15.0, 0.0..180.0, 0.1, "The minimum angle the player must be flying to use y motion") { yMotionSetting && onlyOnDiagonal }
	@Group(Y_MOTION_GROUP) val yMotionStartSpeed by c.setting("Y Motion Start Speed", 30, 0..40, 1, unit = "bps") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val speedLimit by c.setting("Speed Limit", 110, 10..400, 1, unit = "bps") { yMotionSetting }
	context(safeContext: SafeContext)
	private val yMotion
		get() = yMotionSetting &&
				(!onlyOnDiagonal || abs(RotationManager.activeRotation.yaw % 90) > minDiagonalAngle) &&
				safeContext.player.isOnGround &&
				safeContext.player.isGliding &&
				Speedometer.calculateSpeed(true, SpeedUnit.BlocksPerSecond).let { speed ->
					speed > yMotionStartSpeed && speed < speedLimit
				}

	@Group(BOUNCE_OBSTACLE_PASSER_GROUP) override val passerConfig by c.configBlock(PasserSettings(c))

	private var jumpThisTick = false
	private var prevGliding: Boolean? = null
	private val pauseTimer = TickTimer()

	init {
		listen<TickEvent.Pre> {
			pauseTimer.tick()

			if (autoPitch) rotationRequest { pitch(pitch) }.submit()

			if (handlePassingObstacles()) return@listen

			if (!pauseTimer.hasSurpassed(flagPause)) return@listen

			if (!player.isGliding) {
				if (takeoff && player.canTakeoff) {
					if (player.canOpenElytra) {
						player.startGliding()
						startFlyPacket()
					} else jumpThisTick = true
				}
				return@listen
			}

			if (!player.getFlag(Entity.GLIDING_FLAG_INDEX) || yMotion) {
				player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
				startFlyPacket()
			}
		}

		listen<MovementEvent.InputUpdate> { event ->
			if (mode == FlyMode.Bounce && ((player.isGliding && jump) || jumpThisTick)) {
				event.input.jump()
				jumpThisTick = false
			}
		}

		onFlag { pauseTimer.reset() }
	}

	fun getModifiedBounceVelocity(original: Vec3d) =
		runSafe {
			if (!yMotion) return@runSafe original
			else Vec3d(original.x, 0.0, original.z)
		} ?: original

	override fun isGliding(): Boolean? = runSafe {
		val original: Boolean = player.getFlag(Entity.GLIDING_FLAG_INDEX)
		return if (
			mode == FlyMode.Bounce &&
			prevGliding == true &&
			pauseTimer.hasSurpassed(flagPause) &&
			!BaritoneHandler.isActive
		) true
		else {
			prevGliding = original
			original
		}
	}
}