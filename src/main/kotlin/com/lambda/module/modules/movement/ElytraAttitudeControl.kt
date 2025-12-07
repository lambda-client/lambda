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

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.SpeedUnit
import com.lambda.util.Timer
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EntityType
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.seconds

object ElytraAttitudeControl : Module(
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

    val targetSpeed by setting("Target Speed", 28.0, 0.1..50.0, 0.1, unit = " m/s", description = "Adjusts pitch to control speed") { controlValue == Mode.Speed }
	val horizontalSpeed by setting("Horizontal Speed", false, description = "Uses horizontal speed instead of total speed for speed control") { controlValue == Mode.Speed }
	val speedControllerP by setting("Speed Control P", 6.75, 0.0..10.0, 0.05).group(Group.SpeedControl)
	val speedControllerD by setting("Speed Control D", 4.5, 0.0..5.0, 0.05).group(Group.SpeedControl)
	val speedControllerI by setting("Speed Control I", 0.3, 0.0..1.0, 0.05).group(Group.SpeedControl)

	val useFireworkOnHeight by setting("Use Firework On Height", false, "Use fireworks when below a certain height")
	val minHeight by setting("Min Height", 50, 0..256, 10, unit = " blocks", description = "Minimum height to use firework") { useFireworkOnHeight }

	val useFireworkOnSpeed by setting("Use Firework On Speed", false, "Use fireworks based on speed")
	val minSpeed by setting("Min Speed", 20.0, 0.1..50.0, 0.1, unit = " m/s", description = "Minimum speed to use fireworks") { useFireworkOnSpeed }

    var lastPos : Vec3d = Vec3d.ZERO
    val speedController: PIController = PIController({ speedControllerP }, { speedControllerD }, { speedControllerI }, { 0.0 })
	val altitudeController: PIController = PIController({ altitudeControllerP }, { altitudeControllerD }, { altitudeControllerI }, { altitudeControllerConst })

	val usageDelay = Timer()

    init {
        listen<TickEvent.Pre> {
            if (!player.isGliding) return@listen
            if (player.hasFirework && disableOnFirework) return@listen

            val outputPitch = when (controlValue) {
                Mode.Speed -> {
	                var speed = player.pos.subtract(lastPos)
	                if (horizontalSpeed) {
						speed = Vec3d(speed.x, 0.0, speed.z)
	                }

                    speedController.getOutput(targetSpeed, SpeedUnit.MetersPerSecond.convertFromMinecraft(speed.length()))
                }
                Mode.Altitude -> {
                    val currentAltitude = player.y
	                -1 * altitudeController.getOutput(targetAltitude.toDouble(), currentAltitude) // Negative because in minecraft pitch > 0 is looking down not up
                }
            }
            val newPitch = outputPitch.coerceIn(-maxPitchAngle, maxPitchAngle)
//	        lookAt(Rotation(player.yaw, newPitch.toFloat())).requestBy(this@ElytraAutopilot) // TODO: Use this when rotation system accepts pitch changes
	        player.pitch = newPitch.toFloat()

            lastPos = player.pos

	        if (usageDelay.timePassed(2.seconds) && !player.hasFirework) {
		        if (useFireworkOnHeight && minHeight > player.y) {
			        usageDelay.reset()
			        runSafe {
				        startFirework(true)
			        }
		        }
		        if (useFireworkOnSpeed && minSpeed > SpeedUnit.MetersPerSecond.convertFromMinecraft(player.velocity.length())) {
					usageDelay.reset()
			        runSafe {
				        startFirework(true)
			        }
		        }
	        }
        }

        onEnable {
            speedController.reset()
	        altitudeController.reset()
            lastPos = player.pos
        }
    }

    val ClientPlayerEntity.hasFirework: Boolean
        get() = clientWorld.getEntitiesByType(
            EntityType.FIREWORK_ROCKET,
            boundingBox.expand(4.0),
            { it.distanceTo(this) < 4.0 }
        ).isNotEmpty()

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

    enum class Mode {
        Speed,
        Altitude;
    }
    enum class Group(override val displayName: String) : NamedEnum {
        SpeedControl("Speed Control"),
        AltitudeControl("Altitude Control");
    }
}