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

import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.angleDifference
import com.lambda.interaction.request.rotating.Rotation.Companion.dist
import com.lambda.interaction.request.rotating.Rotation.Companion.lerp
import com.lambda.interaction.request.rotating.Rotation.Companion.slerp
import com.lambda.interaction.request.rotating.Rotation.Companion.wrap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the Rotation class
 * Note: Tests only cover methods that don't require SafeContext or Minecraft classes that can't be mocked easily
 */
class RotationTest {

    @Test
    fun `test constructors and basic properties`() {
        // Test double constructor
        val rotation1 = Rotation(45.0, 30.0)
        assertEquals(45.0, rotation1.yaw)
        assertEquals(30.0, rotation1.pitch)

        // Test float constructor
        val rotation2 = Rotation(45f, 30f)
        assertEquals(45.0, rotation2.yaw)
        assertEquals(30.0, rotation2.pitch)

        // Test yawF and pitchF
        assertEquals(45f, rotation1.yawF)
        assertEquals(30f, rotation1.pitchF)

        // Test float property
        val floatArray = rotation1.float
        assertEquals(2, floatArray.size)
        assertEquals(45f, floatArray[0])
        assertEquals(30f, floatArray[1])
    }

    @Test
    fun `test equalFloat method`() {
        val rotation1 = Rotation(45.0, 30.0)
        val rotation2 = Rotation(45.0, 30.0)
        val rotation3 = Rotation(45.0, 31.0)
        val rotation4 = Rotation(46.0, 30.0)

        assertTrue(rotation1.equalFloat(rotation2))
        assertFalse(rotation1.equalFloat(rotation3))
        assertFalse(rotation1.equalFloat(rotation4))
    }

    @Test
    fun `test equals and hashCode methods`() {
        val rotation1 = Rotation(45.0, 30.0)
        val rotation2 = Rotation(45.0, 30.0)
        val rotation3 = Rotation(45.0, 31.0)
        val rotation4 = Rotation(46.0, 30.0)
        val rotation5 = Rotation(46.0, 31.0)

        // Test equals method
        assertEquals(rotation1, rotation2)
        assertNotEquals(rotation1, rotation3)
        assertNotEquals(rotation1, rotation4)
        assertNotEquals(rotation1, rotation5)
        assertNotEquals(rotation3, rotation4)
        assertNotEquals(rotation3, rotation5)
        assertNotEquals(rotation4, rotation5)

        // Test hashCode method
        assertEquals(rotation1.hashCode(), rotation2.hashCode())
        assertNotEquals(rotation1.hashCode(), rotation3.hashCode())
        assertNotEquals(rotation1.hashCode(), rotation4.hashCode())
        assertNotEquals(rotation1.hashCode(), rotation5.hashCode())

        // Test equals with null and other types
        assertNotEquals<Any?>(rotation1, null)
        assertNotEquals<Any>(rotation1, "Not a Rotation")
    }

    @Test
    fun `test withDelta method`() {
        val rotation = Rotation(45.0, 30.0)

        // Test with both yaw and pitch delta
        val result1 = rotation.withDelta(10.0, 20.0)
        assertEquals(55.0, result1.yaw)
        assertEquals(50.0, result1.pitch)

        // Test with only yaw delta
        val result2 = rotation.withDelta(10.0)
        assertEquals(55.0, result2.yaw)
        assertEquals(30.0, result2.pitch)

        // Test with only pitch delta
        val result3 = rotation.withDelta(pitch = 20.0)
        assertEquals(45.0, result3.yaw)
        assertEquals(50.0, result3.pitch)

        // Test pitch clamping (upper bound)
        val result4 = rotation.withDelta(pitch = 70.0)
        assertEquals(45.0, result4.yaw)
        assertEquals(90.0, result4.pitch) // Clamped to 90.0

        // Test pitch clamping (lower bound)
        val result5 = rotation.withDelta(pitch = -130.0)
        assertEquals(45.0, result5.yaw)
        assertEquals(-90.0, result5.pitch) // Clamped to -90.0
    }

    @Test
    fun `test companion object constants`() {
        assertEquals(0.0, Rotation.ZERO.yaw)
        assertEquals(0.0, Rotation.ZERO.pitch)

        assertEquals(0.0, Rotation.DOWN.yaw)
        assertEquals(90.0, Rotation.DOWN.pitch)

        assertEquals(0.0, Rotation.UP.yaw)
        assertEquals(-90.0, Rotation.UP.pitch)
    }

    @Test
    fun `test wrap method`() {
        // Test wrapping positive angles
        assertEquals(0.0, wrap(0.0), 0.001)
        assertEquals(90.0, wrap(90.0), 0.001)
        assertEquals(-180.0, wrap(180.0), 0.001)
        assertEquals(-170.0, wrap(190.0), 0.001) // 190 wraps to -170
        assertEquals(0.0, wrap(360.0), 0.001)
        assertEquals(10.0, wrap(370.0), 0.001)

        // Test wrapping negative angles
        assertEquals(0.0, wrap(-0.0), 0.001)
        assertEquals(-90.0, wrap(-90.0), 0.001)
        assertEquals(-180.0, wrap(-180.0), 0.001)
        assertEquals(170.0, wrap(-190.0), 0.001) // -190 wraps to 170
        assertEquals(0.0, wrap(-360.0), 0.001)
        assertEquals(-10.0, wrap(-370.0), 0.001)

        // Test wrapping large angles
        assertEquals(10.0, wrap(370.0), 0.001)
        assertEquals(10.0, wrap(730.0), 0.001) // 730 = 2*360 + 10
        assertEquals(-10.0, wrap(-370.0), 0.001)
        assertEquals(-10.0, wrap(-730.0), 0.001) // -730 = -2*360 - 10
    }

    @Test
    fun `test lerp method`() {
        val rotation1 = Rotation(0.0, 0.0)
        val rotation2 = Rotation(90.0, 45.0)

        // Test with delta = 0 (should return rotation1)
        val result1 = rotation1.lerp(rotation2, 0.0)
        assertEquals(rotation1.yaw, result1.yaw, 0.001)
        assertEquals(rotation1.pitch, result1.pitch, 0.001)

        // Test with delta = 1 (should return rotation2)
        val result2 = rotation1.lerp(rotation2, 1.0)
        assertEquals(rotation2.yaw, result2.yaw, 0.001)
        assertEquals(rotation2.pitch, result2.pitch, 0.001)

        // Test with delta = 0.5 (should return midpoint)
        val result3 = rotation1.lerp(rotation2, 0.5)
        assertEquals(45.0, result3.yaw, 0.001)
        assertEquals(22.5, result3.pitch, 0.001)
    }

    @Test
    fun `test lerp with angle wrapping`() {
        // Test lerp across the -180/180 boundary
        val rotation1 = Rotation(170.0, 0.0)
        val rotation2 = Rotation(-170.0, 0.0)

        // The shortest path from 170 to -170 is to go clockwise (not counterclockwise)
        // So the midpoint should be -180 (or 180, they're equivalent)
        val midpoint = rotation1.lerp(rotation2, 0.5)

        // Check that the result is either -180 or 180 (they're equivalent)
        assertTrue(
            abs(midpoint.yaw - 180.0) < 0.001 ||
                    abs(midpoint.yaw + 180.0) < 0.001
        )
    }

    @Test
    fun `test slerp method`() {
        val rotation1 = Rotation(0.0, 0.0)
        val rotation2 = Rotation(90.0, 45.0)

        // Test with speed = 0 (should return rotation1)
        val result1 = rotation1.slerp(rotation2, 0.0)
        assertEquals(rotation1.yaw, result1.yaw, 0.001)
        assertEquals(rotation1.pitch, result1.pitch, 0.001)

        // Test with very high speed (should return rotation2)
        val result2 = rotation1.slerp(rotation2, 1000.0)
        assertEquals(rotation2.yaw, result2.yaw, 0.001)
        assertEquals(rotation2.pitch, result2.pitch, 0.001)

        // Test with limited speed
        val result3 = rotation1.slerp(rotation2, 10.0)
        assertTrue(result3.yaw > rotation1.yaw)
        assertTrue(result3.pitch > rotation1.pitch)
        assertTrue(result3.yaw < rotation2.yaw)
        assertTrue(result3.pitch < rotation2.pitch)
    }

    @Test
    fun `test slerp with angle wrapping`() {
        // Test slerp across the -180/180 boundary
        val rotation1 = Rotation(170.0, 0.0)
        val rotation2 = Rotation(-170.0, 0.0)

        // With a very high speed, should go directly to rotation2
        val result = rotation1.slerp(rotation2, 1000.0)
        assertEquals(rotation2.yaw, result.yaw, 0.001)
        assertEquals(rotation2.pitch, result.pitch, 0.001)

        // With a limited speed, should move in the correct direction (clockwise)
        val partialResult = rotation1.slerp(rotation2, 10.0)

        // The yaw should be greater than 170 (moving towards 180/-180)
        // or less than -170 (already crossed the boundary)
        assertTrue(partialResult.yaw > 170.0 || partialResult.yaw < -170.0)
    }

    @Test
    fun `test dist method`() {
        val rotation1 = Rotation(0.0, 0.0)
        val rotation2 = Rotation(90.0, 0.0)
        val rotation3 = Rotation(0.0, 90.0)
        val rotation4 = Rotation(90.0, 90.0)

        // Test distance between same rotations
        assertEquals(0.0, rotation1 dist rotation1, 0.001)

        // Test distance with only yaw difference
        assertEquals(90.0, rotation1 dist rotation2, 0.001)

        // Test distance with only pitch difference
        assertEquals(90.0, rotation1 dist rotation3, 0.001)

        // Test distance with both yaw and pitch difference
        assertEquals(hypot(90.0, 90.0), rotation1 dist rotation4, 0.001)
    }

    @Test
    fun `test dist method with angle wrapping`() {
        // Test distance across the -180/180 boundary
        val rotation1 = Rotation(170.0, 0.0)
        val rotation2 = Rotation(-170.0, 0.0)

        // The distance should be 20 degrees (not 340 degrees)
        assertEquals(20.0, rotation1 dist rotation2, 0.001)
    }

    @Test
    fun `test angleDifference method`() {
        // Test with angles in the same direction
        assertEquals(10.0, angleDifference(10.0, 0.0), 0.001)
        assertEquals(10.0, angleDifference(0.0, 10.0), 0.001)

        // Test with angles in opposite directions
        assertEquals(20.0, angleDifference(-10.0, 10.0), 0.001)
        assertEquals(20.0, angleDifference(10.0, -10.0), 0.001)

        // Test with angles that wrap around
        assertEquals(20.0, angleDifference(170.0, -170.0), 0.001)
        assertEquals(20.0, angleDifference(-170.0, 170.0), 0.001)
    }

    @Test
    fun `test angleDifference with extreme values`() {
        // Test with angles at the boundaries
        assertEquals(0.0, angleDifference(180.0, 180.0), 0.001)
        assertEquals(0.0, angleDifference(-180.0, -180.0), 0.001)
        assertEquals(0.0, angleDifference(180.0, -180.0), 0.001) // These are equivalent

        // Test with large angles that need wrapping
        assertEquals(10.0, angleDifference(365.0, 375.0), 0.001)
        assertEquals(10.0, angleDifference(-365.0, -375.0), 0.001)
        assertEquals(20.0, angleDifference(370.0, -370.0), 0.001)
    }
}
