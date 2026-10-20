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

import com.lambda.interaction.managers.rotating.MouseHand
import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.interaction.managers.rotating.Rotation
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseHandTest {
	private val look = MouseLook(MouseLook.factor(0.5))

	private fun turn(seed: Long, from: Rotation, to: Rotation, limits: MouseHand.Limits, ticks: Int = 200): List<Rotation> {
		val hand = MouseHand(seed)
		val path = arrayListOf(from)
		var rotation = from
		for (tick in 0 until ticks) {
			val step = hand.step(rotation, to, look, limits, tick.toLong())
			hand.commit(step)
			rotation = look.turn(rotation, step.dx, step.dy)
			path += rotation
		}
		return path
	}

	@Test
	fun `a turn settles within half a count of the target`() {
		val target = Rotation(-97.31, 41.2)
		val path = turn(7, Rotation(10f, -5f), target, MouseHand.Limits(speed = 35.0, acceleration = 12.0, noise = 4.0))
		assertTrue(look.reaches(path.last(), target), "settled at ${path.last()}")
		assertTrue(path.takeLast(20).zipWithNext().all { (a, b) -> a == b }, "the hand rests once it arrives")
	}

	@Test
	fun `speed builds up and dies down within the acceleration limit`() {
		val limits = MouseHand.Limits(speed = 40.0, acceleration = 8.0, noise = 0.0)
		val path = turn(1, Rotation(0f, 0f), Rotation(170.0, 0.0), limits)
		val speeds = path.zipWithNext { a, b -> hypot(Rotation.wrap(b.yaw - a.yaw), b.pitch - a.pitch) }
		val slack = look.degreesPerCount * 1.5
		assertTrue(speeds.first() <= limits.acceleration + slack, "first tick turned ${speeds.first()} degrees")
		assertTrue(speeds.zipWithNext().all { (a, b) -> abs(b - a) <= limits.acceleration + slack }, "speeds $speeds")
		assertTrue(speeds.max() <= limits.speed + slack)
	}

	@Test
	fun `the same seed turns the same way and another seed differs`() {
		val limits = MouseHand.Limits(speed = 30.0, acceleration = 10.0, noise = 6.0)
		val from = Rotation(45f, 10f)
		val to = Rotation(-120.0, -30.0)
		assertEquals(turn(3, from, to, limits, 40), turn(3, from, to, limits, 40))
		assertTrue(turn(3, from, to, limits, 40) != turn(4, from, to, limits, 40))
	}
}
