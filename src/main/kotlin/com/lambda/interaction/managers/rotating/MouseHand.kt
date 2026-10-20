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

import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MouseHand(private val seed: Long) {
	data class Limits(val speed: Double, val acceleration: Double, val noise: Double)

	data class Motion(val velocityX: Double = 0.0, val velocityY: Double = 0.0, val carryX: Double = 0.0, val carryY: Double = 0.0)

	data class Step(val dx: Int, val dy: Int, val motion: Motion)

	var motion = Motion()
		private set

	fun step(from: Rotation, to: Rotation, look: MouseLook, limits: Limits, tick: Long): Step {
		if (look.reaches(from, to)) return Step(0, 0, Motion())
		val errorX = Rotation.wrap(to.yaw - from.yaw)
		val errorY = to.pitch - from.pitch
		val distance = hypot(errorX, errorY)
		val cap = (limits.speed + limits.noise * gaussian(tick)).coerceAtLeast(look.degreesPerCount)
		val braking = limits.acceleration * (sqrt(2.0 * distance / limits.acceleration + 0.25) - 0.5)
		val speed = minOf(cap, braking, distance)
		var velocityX = speed * errorX / distance
		var velocityY = speed * errorY / distance
		val changeX = velocityX - motion.velocityX
		val changeY = velocityY - motion.velocityY
		val change = hypot(changeX, changeY)
		if (change > limits.acceleration) {
			velocityX = motion.velocityX + changeX * limits.acceleration / change
			velocityY = motion.velocityY + changeY * limits.acceleration / change
		}
		val wantX = velocityX / look.degreesPerCount + motion.carryX
		val wantY = velocityY / look.degreesPerCount + motion.carryY
		val countsX = wantX.roundToInt()
		val countsY = wantY.roundToInt()
		return Step(
			if (look.invertX) -countsX else countsX,
			if (look.invertY) -countsY else countsY,
			Motion(velocityX, velocityY, wantX - countsX, wantY - countsY),
		)
	}

	fun commit(step: Step) {
		motion = step.motion
	}

	fun rest() {
		motion = Motion()
	}

	private fun gaussian(tick: Long): Double {
		val random = SplittableRandom(seed * GOLDEN_GAMMA + tick)
		val u1 = 1.0 - random.nextDouble()
		val u2 = random.nextDouble()
		return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
	}

	private companion object {
		const val GOLDEN_GAMMA = -0x61c8864680b583ebL
	}
}
