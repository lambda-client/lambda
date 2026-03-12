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

import com.lambda.util.math.Vec2d
import com.lambda.util.math.coerceIn
import com.lambda.util.math.inv
import com.lambda.util.math.lerp
import com.lambda.util.math.normalize
import com.lambda.util.math.random
import com.lambda.util.math.step
import com.lambda.util.math.transform
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class RangeTest {

    @Test
    fun `test step over double range`() {
        val range = 0.0..10.0 step 2.0

        val result = range.asSequence().toList()
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), result)
    }

    @Test
    fun `test step over float range`() {
        val range = 0.0f..10.0f step 2.0f

        val result = range.asSequence().toList()
        assertEquals(listOf(0.0f, 2.0f, 4.0f, 6.0f, 8.0f, 10.0f), result)
    }

    @Test
    fun `test random within range`() {
        val range = 0.0..10.0
        val randomValue = range.random()

        assertTrue(randomValue in range)  // The value must be in the range [0.0, 10.0]
    }

    @Test
    fun `test normalize double value`() {
        val range = 0.0..100.0
        val normalized = range.normalize(50.0)

        assertEquals(0.5, normalized)  // 50.0 should be normalized to 0.5 in the range [0.0, 100.0]
    }

    @Test
    fun `test normalize float value`() {
        val range = 0f..100f
        val normalized = range.normalize(50f)

        assertEquals(0.5f, normalized)  // 50f should be normalized to 0.5f in the range [0f, 100f]
    }

    @Test
    fun `test invert float range`() {
        val range = 0f..100f
        val inverted = range.inv()

        assertEquals(100f to 0f, inverted)  // Inverting the range [0f, 100f] gives (100f, 0f)
    }

    @Test
    fun `test transform double range`() {
        val range = 0.0..10.0
        val transformed = range.transform(5.0, 0.0, 100.0)

        assertEquals(50.0, transformed)  // 5.0 in range [0.0, 10.0] maps to 50.0 in range [0.0, 100.0]
    }

    @Test
    fun `test transform float range`() {
        val range = 0f..10f
        val transformed = range.transform(5f, 0f, 100f)

        assertEquals(50f, transformed)  // 5f in range [0f, 10f] maps to 50f in range [0f, 100f]
    }

    @Test
    fun `test lerp double values`() {
        val lerpedValue = lerp(0.5, 0.0, 10.0)

        assertEquals(5.0, lerpedValue)  // Linear interpolation between 0.0 and 10.0 at 0.5 results in 5.0
    }

    @Test
    fun `test lerp float values`() {
        val lerpedValue = lerp(0.5f, 0f, 10f)

        assertEquals(5.0f, lerpedValue)  // Linear interpolation between 0f and 10f at 0.5 results in 5.0f
    }

    @Test
    fun `test lerp Vec2d`() {
        val start = Vec2d(0.0, 0.0)
        val end = Vec2d(10.0, 10.0)
        val lerpedValue = lerp(0.5, start, end)

        assertEquals(Vec2d(5.0, 5.0), lerpedValue)  // Interpolated 50% between (0, 0) and (10, 10)
    }

    @Test
    fun `test lerp Vec3d`() {
        val start = Vec3d(0.0, 0.0, 0.0)
        val end = Vec3d(10.0, 10.0, 10.0)
        val lerpedValue = lerp(0.5, start, end)

        assertEquals(Vec3d(5.0, 5.0, 5.0), lerpedValue)  // Interpolated 50% between (0, 0, 0) and (10, 10, 10)
    }

    @Test
    fun `test lerp Color`() {
        val start = Color(255, 0, 0)  // Red
        val end = Color(0, 0, 255)    // Blue
        val lerpedValue = lerp(0.5, start, end)

        assertEquals(Color(128, 0, 128), lerpedValue)  // Interpolated color should be purple
    }

    @Test
    fun `test coercing value in double range`() {
        val range = 0.0..10.0
        val coercedValue = range.coerceIn(15.0)

        assertEquals(10.0, coercedValue)  // Coerced value should be within the range [0.0, 10.0]
    }

    @Test
    fun `test coercing value in float range`() {
        val range = 0f..10f
        val coercedValue = range.coerceIn(15f)

        assertEquals(10f, coercedValue)  // Coerced value should be within the range [0f, 10f]
    }

    @Test
    fun `test coercing value in 2d vector`() {
        val vec = Vec2d(5.0, 5.0)
        val coercedVec = vec.coerceIn(0.0, 10.0, 0.0, 10.0)

        assertEquals(Vec2d(5.0, 5.0), coercedVec)  // Vec should stay the same
    }
}
