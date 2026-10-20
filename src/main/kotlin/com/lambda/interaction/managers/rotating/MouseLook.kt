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

package com.lambda.interaction.managers.rotating

import kotlin.math.abs
import kotlin.math.roundToInt

data class MouseLook(val factor: Double, val invertX: Boolean = false, val invertY: Boolean = false) {
	val degreesPerCount: Double get() = (factor.toFloat() * TURN_SCALE).toDouble()

	fun turn(rotation: Rotation, dx: Int, dy: Int): Rotation {
		val x = dx * factor
		val y = dy * factor
		val yaw = rotation.yawF + (if (invertX) -x else x).toFloat() * TURN_SCALE
		val pitch = ((rotation.pitchF + (if (invertY) -y else y).toFloat() * TURN_SCALE) % 360f).coerceIn(-90f, 90f)
		return Rotation(yaw, pitch)
	}

	fun countsX(degrees: Double): Int = (degrees / degreesPerCount).roundToInt().let { if (invertX) -it else it }

	fun countsY(degrees: Double): Int = (degrees / degreesPerCount).roundToInt().let { if (invertY) -it else it }

	fun nearest(rotation: Rotation, target: Rotation): Rotation =
		turn(rotation, countsX(Rotation.wrap(target.yaw - rotation.yaw)), countsY(target.pitch - rotation.pitch))

	fun reaches(rotation: Rotation, target: Rotation): Boolean =
		abs(Rotation.wrap(target.yaw - rotation.yaw)) <= degreesPerCount / 2 && abs(target.pitch - rotation.pitch) <= degreesPerCount / 2

	companion object {
		const val TURN_SCALE = 0.15f

		fun factor(sensitivity: Double, spyglass: Boolean = false): Double {
			val d = sensitivity * 0.6f + 0.2f
			val cubed = d * d * d
			return if (spyglass) cubed else cubed * 8.0
		}
	}
}
