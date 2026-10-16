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

package com.lambda.module.modules.player

import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.editSetting
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import kotlin.math.roundToInt

@Suppress("unused")
object RotationLock : Module(
	name = "RotationLock",
	description = "Locks the player rotation to the given configuration",
	tag = ModuleTag.PLAYER,
	modulePriority = 100
) {
	@JvmStatic val yawMode by setting("Yaw Mode", Mode.Snap)
	private val yawStep by setting("Yaw Step", 45.0, 1.0..180.0, 0.1) { yawMode == Mode.Snap }
	private val customYaw by setting("Custom Yaw", 0.0, -179.0..180.0, 0.1) { yawMode == Mode.Custom }
	@JvmStatic val pitchMode by setting("Pitch Mode", Mode.None)
	private val pitchStep by setting("Pitch Step", 45.0, 1.0..90.0, 0.1) { pitchMode == Mode.Snap }
	private val customPitch by setting("Custom Pitch", 0.0, -90.0..90.0, 0.1) { pitchMode == Mode.Custom }

    init {
	    setDefaultAutomationConfig()
		    .withEdits {
				hideAllExcept(::rotationConfig)
		        rotationConfig::rotationMode.editSetting { defaultValue(RotationMode.Lock) }
	        }

        listen<TickEvent.Pre> {
            val yaw = when (yawMode) {
                Mode.Custom -> customYaw
                Mode.Snap -> {
                    val normalizedYaw = (player.yaw % 360.0 + 360.0) % 360.0
                    (normalizedYaw / yawStep).roundToInt() * yawStep
                }
                Mode.None -> null
            }
            val pitch = when (pitchMode) {
                Mode.Custom -> customPitch
                Mode.Snap -> {
                    val clampedPitch = player.pitch.coerceIn(-90f, 90f)
                    (clampedPitch / pitchStep).roundToInt() * pitchStep
                }
                Mode.None -> null
            }

			if (yaw == null && pitch == null) return@listen

			rotationRequest {
				yaw?.let { yaw(it) }
				pitch?.let { pitch(it) }
			}.submit()
		}
	}

	enum class Mode {
		Snap,
		Custom,
		None
	}
}
