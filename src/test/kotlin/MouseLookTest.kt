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

import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.interaction.managers.rotating.Rotation
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseLookTest {
	private fun vanilla(yaw: Float, pitch: Float, cursorX: Double, cursorY: Double, sensitivity: Double): Pair<Float, Float> {
		val d = sensitivity * 0.6f + 0.2f
		val f = d * d * d * 8.0
		val i = cursorX * f
		val j = cursorY * f
		val pitchStep = j.toFloat() * 0.15f
		val yawStep = i.toFloat() * 0.15f
		val turnedPitch = ((pitch + pitchStep) % 360.0f).coerceIn(-90.0f, 90.0f)
		return (yaw + yawStep) to turnedPitch.coerceIn(-90.0f, 90.0f)
	}

	@Test
	fun `a turn lands on the same floats as vanilla mouse input`() {
		for (sensitivity in listOf(0.0, 0.25, 0.5, 0.83, 1.0)) {
			val look = MouseLook(MouseLook.factor(sensitivity))
			var rotation = Rotation(123.4f, -12.5f)
			var yaw = rotation.yawF
			var pitch = rotation.pitchF
			for ((dx, dy) in listOf(3 to -7, 0 to 0, -41 to 12, 250 to 400, 1 to -1)) {
				rotation = look.turn(rotation, dx, dy)
				val (vanillaYaw, vanillaPitch) = vanilla(yaw, pitch, dx.toDouble(), dy.toDouble(), sensitivity)
				yaw = vanillaYaw
				pitch = vanillaPitch
				assertEquals(yaw, rotation.yawF, "yaw at sensitivity $sensitivity")
				assertEquals(pitch, rotation.pitchF, "pitch at sensitivity $sensitivity")
			}
		}
	}

	@Test
	fun `every turn is a whole number of counts`() {
		val look = MouseLook(MouseLook.factor(0.5))
		val step = look.degreesPerCount
		var rotation = Rotation(0f, 0f)
		for (counts in listOf(1, 7, -13, 40, -2)) {
			val next = look.turn(rotation, counts, 0)
			val delta = next.yaw - rotation.yaw
			val multiple = delta / step
			assertTrue(abs(multiple - multiple.roundToLong()) < 1e-4, "a turn of $counts counts moved $delta degrees")
			rotation = next
		}
	}

	@Test
	fun `pitch stops at the vertical and inversion flips the direction`() {
		val look = MouseLook(MouseLook.factor(0.5))
		assertEquals(90f, look.turn(Rotation(0f, 89f), 0, 100).pitchF)
		assertEquals(-90f, look.turn(Rotation(0f, -89f), 0, -100).pitchF)
		val inverted = MouseLook(MouseLook.factor(0.5), invertX = true, invertY = true)
		val turned = inverted.turn(Rotation(10f, 10f), 5, 5)
		assertTrue(turned.yaw < 10.0 && turned.pitch < 10.0)
		assertEquals(-5, inverted.countsX(10.0 - turned.yaw))
	}

	@Test
	fun `the nearest reachable rotation is within half a count of the target`() {
		val look = MouseLook(MouseLook.factor(0.37))
		val start = Rotation(-170f, 20f)
		val target = Rotation(175.3, -33.71)
		val landed = look.nearest(start, target)
		assertTrue(look.reaches(landed, target), "landed on $landed for $target")
	}
}
