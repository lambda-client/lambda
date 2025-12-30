/*
 * Copyright 2025 Lambda
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

import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.NamedEnum
import com.lambda.util.SpeedUnit
import com.lambda.util.world.fastEntitySearch
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.text.Text.literal
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource


object ElytraAltitudeControl : Module(
	name = "ElytraAttitudeControl",
	description = "Automatically control attitude or speed while elytra flying",
	tag = ModuleTag.MOVEMENT,
) {
	val controlValue by setting("Control Value", Mode.Altitude)

	val maxPitchAngle by setting("Max Pitch Angle", 45.0, 0.0..90.0, 1.0, unit = "°", description = "Maximum pitch angle")
	val disableOnFirework by setting("Disable On Firework", false, description = "Disables the module when a firework is used")

	val targetAltitude by setting("Target Altitude", 120, 0..256, 10, unit = " blocks", description = "Adjusts pitch to control altitude") { controlValue == Mode.Altitude }
	val altitudeControllerP by setting("Altitude Control P", 1.2, 0.0..2.0, 0.05).group(Group.AltitudeControl)
	val altitudeControllerD by setting("Altitude Control D", 0.85, 0.0..1.0, 0.05).group(Group.AltitudeControl)
	val altitudeControllerI by setting("Altitude Control I", 0.04, 0.0..1.0, 0.05).group(Group.AltitudeControl)
	val altitudeControllerConst by setting("Altitude Control Const", 0.0, 0.0..10.0, 0.1).group(Group.AltitudeControl)

	val targetSpeed by setting("Target Speed", 20.0, 0.1..50.0, 0.1, unit = " m/s", description = "Adjusts pitch to control speed") { controlValue == Mode.Speed }
	val horizontalSpeed by setting("Horizontal Speed", false, description = "Uses horizontal speed instead of total speed for speed control") { controlValue == Mode.Speed }
	val speedControllerP by setting("Speed Control P", 6.75, 0.0..10.0, 0.05).group(Group.SpeedControl)
	val speedControllerD by setting("Speed Control D", 4.5, 0.0..5.0, 0.05).group(Group.SpeedControl)
	val speedControllerI by setting("Speed Control I", 0.3, 0.0..1.0, 0.05).group(Group.SpeedControl)

	val useFireworkOnHeight by setting("Use Firework On Height", false, "Use fireworks when below a certain height")
	val minHeight by setting("Min Height", 50, 0..256, 10, unit = " blocks", description = "Minimum height to use firework") { useFireworkOnHeight }

	val useFireworkOnSpeed by setting("Use Firework On Speed", false, "Use fireworks based on speed")
	val minSpeed by setting("Min Speed", 20.0, 0.1..50.0, 0.1, unit = " m/s", description = "Minimum speed to use fireworks") { useFireworkOnSpeed }

	var lastPos: Vec3d = Vec3d.ZERO
	val speedController: PIController = PIController({ speedControllerP }, { speedControllerD }, { speedControllerI }, { 0.0 })
	val altitudeController: PIController = PIController({ altitudeControllerP }, { altitudeControllerD }, { altitudeControllerI }, { altitudeControllerConst })

	val usePitch40OnHeight by setting("Use Pitch 40 On Height", false, "Use Pitch 40 to gain height and speed")
	val logHeightGain by setting("Log Height Gain", false, "Logs the height gained each cycle to the chat") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val minHeightForPitch40 by setting("Min Height For Pitch 40", 120, 0..256, 10, unit = " blocks", description = "Minimum height to use Pitch 40") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40ExitHeight by setting("Exit height", 190, 0..256, 10, unit = " blocks", description = "Height to exit Pitch 40 mode") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40UpStartAngle by setting("Up Start Angle", -49f, -90f..0f, .5f, description = "Start angle when going back up. negative pitch = looking up") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40DownAngle by setting("Down Angle", 33f, 0f..90f, .5f, description = "Angle to dive down at to gain speed") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40AngleChangeRate by setting("Angle Change Rate", 0.5f, 0.1f..5f, 0.01f, description = "Rate at which to increase pitch while in the fly up curve") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40SpeedThreshold by setting("Speed Threshold", 41f, 10f..100f, .5f, description = "Speed at which to start pitching up") { usePitch40OnHeight }.group(Group.Pitch40Control)
	val pitch40UseFireworkOnUpTrajectory by setting("Use Firework On Up Trajectory", false, "Use fireworks when converting speed to altitude in the Pitch 40 maneuver") { usePitch40OnHeight }.group(Group.Pitch40Control)

	val useTimerOnChunkLoad by setting("Use Timer On Slow Chunk Loading", false, "Slows down the game when chunks load slow to keep momentum").group(Group.TimerControls)
	val timerMinChunkDistance by setting("Min Chunk Distance", 4, 1..20, 1, "Min unloaded chunk distance to start timer effect", unit = " chunks") { useTimerOnChunkLoad }.group(Group.TimerControls)
	val timerReturnValue by setting("Timer Return Value", 1.0f, 0.0f..1.0f, 0.05f, description = "Timer speed to return when above min chunk distance") { useTimerOnChunkLoad }.group(Group.TimerControls)

	override val rotationConfig = RotationSettings(this, Group.Rotation)

	var controlState = ControlState.AttitudeControl
	var state = Pitch40State.GainSpeed
	var lastAngle = pitch40UpStartAngle
	var lastCycleFinish = TimeSource.Monotonic.markNow()
	var lastY = 0.0

	val usageDelay = com.lambda.util.Timer()

	init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllGroupsExcept(rotationConfig)
			}
		}

		listen<TickEvent.Pre> {
			if (player.isGliding) {
				when (controlState) {
					ControlState.AttitudeControl -> updateAltitudeControls()
					ControlState.Pitch40Fly -> updatePitch40Controls()
				}
				updateTimerUsage()
				lastPos = player.pos
			}
		}

		onEnable {
			speedController.reset()
			altitudeController.reset()
			lastPos = player.pos
			state = Pitch40State.GainSpeed
			controlState = ControlState.AttitudeControl
			lastAngle = pitch40UpStartAngle
		}

		onDisable {
			if (useTimerOnChunkLoad) {
				Timer.timer = timerReturnValue.toDouble()
			}
		}
	}

	private fun SafeContext.updateAltitudeControls() {
		if (disableOnFirework && hasFirework) {
			return
		}
		if (usePitch40OnHeight) {
			if (player.y < minHeightForPitch40) {
				controlState = ControlState.Pitch40Fly
				lastY = player.pos.y
				return
			}
		}
		val outputPitch = when (controlValue) {
			Mode.Speed -> {
				speedController.getOutput(targetSpeed, player.flySpeed(horizontalSpeed).toDouble())
			}
			Mode.Altitude -> {
				-1 * altitudeController.getOutput(targetAltitude.toDouble(), player.y) // Negative because in minecraft pitch > 0 is looking down not up
			}
		}.coerceIn(-maxPitchAngle, maxPitchAngle)
		RotationRequest(Rotation(player.yaw, outputPitch.toFloat()), this@ElytraAltitudeControl).submit()

		if (usageDelay.timePassed(2.seconds) && !hasFirework) {
			if (useFireworkOnHeight && minHeight > player.y) {
				usageDelay.reset()
				runSafe {
					startFirework(true)
				}
			}
			if (useFireworkOnSpeed && minSpeed > player.flySpeed()) {
				usageDelay.reset()
				runSafe {
					startFirework(true)
				}
			}
		}
	}

	private fun SafeContext.updatePitch40Controls() {
		when (state) {
			Pitch40State.GainSpeed -> {
				rotationRequest { pitch(pitch40DownAngle) }.submit()
				if (player.flySpeed() > pitch40SpeedThreshold) {
					state = Pitch40State.PitchUp
				}
			}
			Pitch40State.PitchUp -> {
				lastAngle -= 5f
				rotationRequest { pitch(lastAngle) }.submit()
				if (lastAngle <= pitch40UpStartAngle) {
					state = Pitch40State.FlyUp
					if (pitch40UseFireworkOnUpTrajectory) {
						runSafe {
							startFirework(true)
						}
					}
				}
			}
			Pitch40State.FlyUp -> {
				lastAngle += pitch40AngleChangeRate
				rotationRequest { pitch(lastAngle) }.submit()
				if (lastAngle >= 0f) {
					state = Pitch40State.GainSpeed
					if (logHeightGain) {
						val timeDelta = lastCycleFinish.elapsedNow().inWholeMilliseconds
						val heightDelta = player.pos.y - lastY
						val heightPerMinute = (heightDelta) / (timeDelta / 1000.0) * 60.0
						info(literal("Height gained this cycle: %.2f in %.2f seconds (%.2f blocks/min)".format(heightDelta, timeDelta / 1000.0, heightPerMinute)))
					}

					lastCycleFinish = TimeSource.Monotonic.markNow()
					lastY = player.pos.y
					if (pitch40ExitHeight < player.y) {
						controlState = ControlState.AttitudeControl
						speedController.reset()
						altitudeController.reset()
					}
				}
			}
		}
	}

	private fun SafeContext.updateTimerUsage() {
		if (useTimerOnChunkLoad) {
			val nearestChunkDistance = getNearestUnloadedChunkDistance()
			if (nearestChunkDistance != -1 && nearestChunkDistance / 16.0 <= timerMinChunkDistance) {
				val speedFactor = 0.1f + (nearestChunkDistance.toFloat() / timerMinChunkDistance.toFloat() * 16.0) * 0.9f
				Timer.enable()
				Timer.timer = speedFactor.coerceIn(0.1, 1.0)
			} else {
				if (Timer.isEnabled) {
					Timer.timer = timerReturnValue.toDouble()
				}
			}
		}
	}

	val hasFirework: Boolean
		get() = runSafe { return fastEntitySearch<FireworkRocketEntity>(4.0) { it.shooter == player }.any() } ?: false

	private fun SafeContext.getNearestUnloadedChunkDistance(): Int {
		val nearestChunk: ChunkPos? = nearestUnloadedChunk(world, player)
		return if (nearestChunk != null) distanceToChunk(nearestChunk, player).toInt() else -1
	}

	fun nearestUnloadedChunk(world: ClientWorld, player: ClientPlayerEntity): ChunkPos? {
		val scanRangeInt = 25
		var nearestChunk: ChunkPos? = null
		var nearestDistance = Double.MAX_VALUE
		val playerChunk = player.chunkPos

		for (x in -scanRangeInt..<scanRangeInt) {
			for (z in -scanRangeInt..<scanRangeInt) {
				val chunkPos = ChunkPos(playerChunk.x + x, playerChunk.z + z)
				if (world.chunkManager.isChunkLoaded(chunkPos.x, chunkPos.z)) {
					continue
				}
				val distance = distanceToChunk(chunkPos, player).toDouble()
				if (distance < nearestDistance) {
					nearestDistance = distance
					nearestChunk = chunkPos
				}
			}
		}
		return nearestChunk
	}

	fun distanceToChunk(chunkPos: ChunkPos, player: ClientPlayerEntity): Float {
		val playerPos = player.getPos()
		val chunkCenter = Vec3d((chunkPos.startX + 8).toDouble(), playerPos.y, (chunkPos.startZ + 8).toDouble())
		return playerPos.distanceTo(chunkCenter).toFloat()
	}

	class PIController(val valueP: () -> Double, val valueD: () -> Double, val valueI: () -> Double, val constant: () -> Double) {
		var accumulator = 0.0 // Integral term accumulator
		var lastDiff = 0.0
		fun getOutput(target: Double, current: Double): Double {
			val diff = target - current
			val diffDt = diff - lastDiff
			accumulator += diff

			accumulator = accumulator.coerceIn(-100.0, 100.0) // Prevent integral windup
			lastDiff = diff

			return diffDt * valueD() + diff * valueP() + accumulator * valueI() + constant()
		}

		fun reset() {
			accumulator = 0.0
		}
	}

	/**
	 * Get the player's current speed in meters per second.
	 */
	fun ClientPlayerEntity.flySpeed(onlyHorizontal: Boolean = false): Float {
		var delta = this.pos.subtract(lastPos)
		if (onlyHorizontal) {
			delta = Vec3d(delta.x, 0.0, delta.z)
		}
		return SpeedUnit.MetersPerSecond.convertFromMinecraft(delta.length()).toFloat()
	}

	enum class Mode {
		Speed,
		Altitude;
	}

	enum class ControlState {
		AttitudeControl,
		Pitch40Fly
	}

	enum class Group(override val displayName: String) : NamedEnum {
		SpeedControl("Speed Control"),
		AltitudeControl("Altitude Control"),
		Pitch40Control("Pitch 40 Control"),
		Rotation("Rotation"),
		TimerControls("Timer Controls"),
	}

	enum class Pitch40State {
		GainSpeed,
		PitchUp,
		FlyUp,
	}
}