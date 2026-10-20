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

package pathing

import com.lambda.interaction.managers.rotating.MouseHand
import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.Turning
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurningTest {
	private val look = MouseLook(MouseLook.factor(0.5))

	private fun state(rotation: Rotation, turn: Rotation = Rotation.ZERO, x: Double = 0.0) =
		MovementSimulationState.synthetic(ProbeScenarios.PROFILE, Vec3d(x, 0.0, 0.0), rotation).copy(turn = turn)

	private fun turns(turning: Turning, from: Rotation, target: Rotation, ticks: Int): List<Rotation> {
		var current = state(from)
		val path = arrayListOf(from)
		repeat(ticks) { tick ->
			val next = turning.turn(current, target)
			current = state(next, Rotation(Rotation.wrap(next.yaw - current.rotation.yaw), next.pitch - current.rotation.pitch), x = tick.toDouble())
			path += next
		}
		return path
	}

	@Test
	fun `without a mouse look a turn is clamped like before`() {
		val turning = Turning(30.0)
		assertEquals(Rotation(40.0, 0.0), turning.yawTo(state(Rotation(10.0, 0.0)), 100.0))
		assertEquals(Rotation(-5.0, 0.0), turning.yawTo(state(Rotation(10.0, 0.0)), -5.0))
		assertEquals(Rotation(25.0, 0.0), turning.yawTo(state(Rotation(10.0, 0.0)), 100.0, cap = 15.0))
	}

	@Test
	fun `with a look and no hand a turn is the nearest whole count`() {
		val turning = Turning(30.0, look)
		val from = Rotation(10.3f, 2.1f)
		assertEquals(look.nearest(from, Rotation(40.3, 2.1)), turning.yawTo(state(from), 100.0))
	}

	@Test
	fun `a hand speeds up within its acceleration and settles on the target`() {
		val limits = MouseHand.Limits(speed = 40.0, acceleration = 8.0, noise = 0.0)
		val target = Rotation(170.0, -20.0)
		val path = turns(Turning(40.0, look, limits), Rotation(0f, 0f), target, 120)
		val steps = path.zipWithNext { a, b -> Rotation(Rotation.wrap(b.yaw - a.yaw), b.pitch - a.pitch) }
		val slack = look.degreesPerCount * 1.5
		assertTrue(steps.all { hypot(it.yaw, it.pitch) <= limits.speed + slack }, "speeds $steps")
		assertTrue(steps.zipWithNext().all { (a, b) -> hypot(b.yaw - a.yaw, b.pitch - a.pitch) <= limits.acceleration + slack }, "accelerations $steps")
		assertTrue(look.reaches(path.last(), target), "settled at ${path.last()}")
		assertTrue(path.zipWithNext().all { (a, b) -> look.turn(a, look.countsX(Rotation.wrap(b.yaw - a.yaw)), look.countsY(b.pitch - a.pitch)) == b })
	}

	@Test
	fun `a hold while turning fast slows down within the acceleration`() {
		val limits = MouseHand.Limits(speed = 30.0, acceleration = 6.0, noise = 0.0)
		val turning = Turning(30.0, look, limits)
		var current = state(Rotation(0f, 0f), Rotation(18.0, 0.0))
		val held = current.rotation
		val speeds = arrayListOf(18.0)
		repeat(12) {
			val next = turning.turn(current, held)
			val turn = Rotation(Rotation.wrap(next.yaw - current.rotation.yaw), next.pitch - current.rotation.pitch)
			speeds += hypot(turn.yaw, turn.pitch) * Math.signum(turn.yaw)
			current = state(next, turn, x = it.toDouble())
		}
		val slack = look.degreesPerCount * 1.5
		assertTrue(speeds.zipWithNext().all { (a, b) -> abs(b - a) <= limits.acceleration + slack }, "speeds $speeds")
		assertTrue(look.reaches(current.rotation, held) && abs(speeds.last()) < limits.acceleration, "ended at ${current.rotation}, speeds $speeds")
	}

	@Test
	fun `the same state always draws the same noise`() {
		val turning = Turning(40.0, look, MouseHand.Limits(speed = 20.0, acceleration = 30.0, noise = 8.0))
		val observed = state(Rotation(5f, 1f), Rotation(12.0, 0.0), x = 3.25)
		assertEquals(turning.yawTo(observed, -120.0), turning.yawTo(observed, -120.0))
		val speeds = (0 until 40).map { tick ->
			val moved = turning.yawTo(state(Rotation(5f, 1f), Rotation(12.0, 0.0), x = tick * 0.37), -120.0)
			abs(Rotation.wrap(moved.yaw - 5.0))
		}
		assertTrue(speeds.toSet().size > 3, "noise never changed the speed: $speeds")
	}
}
