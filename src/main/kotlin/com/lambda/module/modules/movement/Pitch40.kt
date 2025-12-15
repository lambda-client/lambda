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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.SpeedUnit
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.text.Text.literal
import net.minecraft.util.math.Vec3d

object Pitch40 : Module(
	name = "Pitch40",
	description = "Allows you to fly forever",
	tag = ModuleTag.MOVEMENT
) {
	val logHeightGain by setting("Log Height Gain", true, "Logs the height gained each cycle to the chat")

	const val PITCH_UP_DEFAULT = -49f  // Start angle when going back up. negative pitch = looking up
	const val PITCH_DOWN_DEFAULT = 33f // Best angle for getting speed
	const val PITCH_ANGLE_CHANGE_SPEED = 0.5f
	const val PITCH_UP_SPEED_THRESHOLD = 45f

	var state = Pitch40State.GainSpeed
	var lastAngle = PITCH_UP_DEFAULT

	var lastPos: Vec3d = Vec3d.ZERO
	var lastY = 0.0

	init {
		setDefaultAutomationConfig {
			applyEdits {
				hideAllGroupsExcept(rotationConfig)
			}
		}

		listen<TickEvent.Pre> {
			when (state) {
				Pitch40State.GainSpeed -> {
					lookAt(Rotation(player.yaw, PITCH_DOWN_DEFAULT)).requestBy(this@Pitch40)
					if (player.flySpeed() > PITCH_UP_SPEED_THRESHOLD) {
						state = Pitch40State.PitchUp
					}
				}
				Pitch40State.PitchUp -> {
					lastAngle -= 5f
					lookAt(Rotation(player.yaw, lastAngle)).requestBy(this@Pitch40)
					if (lastAngle <= PITCH_UP_DEFAULT) {
						state = Pitch40State.FlyUp
					}
				}
				Pitch40State.FlyUp -> {
					lastAngle += PITCH_ANGLE_CHANGE_SPEED
					lookAt(Rotation(player.yaw, lastAngle)).requestBy(this@Pitch40)
					if (lastAngle >= 0f) {
						state = Pitch40State.GainSpeed
						if (logHeightGain)
							info(literal("Height gained this cycle: %.2f meters".format(player.pos.y - lastY)))

						lastY = player.pos.y
					}
				}
			}
			lastPos = player.pos
		}

		onEnable {
			state = Pitch40State.GainSpeed
			lastPos = player.pos
			lastAngle = PITCH_UP_DEFAULT
		}
	}

	/**
	 * Get the player's current speed in meters per second.
	 */
	fun ClientPlayerEntity.flySpeed(): Float {
		val delta = this.pos.subtract(lastPos)
		return SpeedUnit.MetersPerSecond.convertFromMinecraft(delta.length()).toFloat()
	}

	enum class Pitch40State {
		GainSpeed,
		PitchUp,
		FlyUp,
	}
}