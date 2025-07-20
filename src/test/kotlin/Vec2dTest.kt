import com.lambda.util.math.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals

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

class Vec2dTest {
    @Test
    fun `test unary minus`() {
        val vector = Vec2d(1.0, -2.0)
        val result = -vector

        assertEquals(Vec2d(-1.0, 2.0), result)
    }

    @Test
    fun `test addition with another Vec2d`() {
        val vector1 = Vec2d(1.0, 2.0)
        val vector2 = Vec2d(3.0, 4.0)
        val result = vector1 + vector2

        assertEquals(Vec2d(4.0, 6.0), result)
    }

    @Test
    fun `test addition with scalar (Double)`() {
        val vector = Vec2d(1.0, 2.0)
        val result = vector + 3.0

        assertEquals(Vec2d(4.0, 5.0), result)
    }

    @Test
    fun `test addition with scalar (Float)`() {
        val vector = Vec2d(1.0, 2.0)
        val result = vector + 3.0f

        assertEquals(Vec2d(4.0, 5.0), result)
    }

    @Test
    fun `test addition with scalar (Int)`() {
        val vector = Vec2d(1.0, 2.0)
        val result = vector + 3

        assertEquals(Vec2d(4.0, 5.0), result)
    }

    @Test
    fun `test subtraction with another Vec2d`() {
        val vector1 = Vec2d(5.0, 6.0)
        val vector2 = Vec2d(3.0, 4.0)
        val result = vector1 - vector2

        assertEquals(Vec2d(2.0, 2.0), result)
    }

    @Test
    fun `test subtraction with scalar (Double)`() {
        val vector = Vec2d(5.0, 6.0)
        val result = vector - 2.0

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test subtraction with scalar (Float)`() {
        val vector = Vec2d(5.0, 6.0)
        val result = vector - 2.0f

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test subtraction with scalar (Int)`() {
        val vector = Vec2d(5.0, 6.0)
        val result = vector - 2

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test multiplication with scalar (Double)`() {
        val vector = Vec2d(2.0, 3.0)
        val result = vector * 2.0

        assertEquals(Vec2d(4.0, 6.0), result)
    }

    @Test
    fun `test multiplication with scalar (Float)`() {
        val vector = Vec2d(2.0, 3.0)
        val result = vector * 2.0f

        assertEquals(Vec2d(4.0, 6.0), result)
    }

    @Test
    fun `test multiplication with scalar (Int)`() {
        val vector = Vec2d(2.0, 3.0)
        val result = vector * 2

        assertEquals(Vec2d(4.0, 6.0), result)
    }

    @Test
    fun `test multiplication with another Vec2d`() {
        val vector1 = Vec2d(2.0, 3.0)
        val vector2 = Vec2d(4.0, 5.0)
        val result = vector1 * vector2

        assertEquals(Vec2d(8.0, 15.0), result)
    }

    @Test
    fun `test division with scalar (Double)`() {
        val vector = Vec2d(6.0, 8.0)
        val result = vector / 2.0

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test division with scalar (Float)`() {
        val vector = Vec2d(6.0, 8.0)
        val result = vector / 2.0f

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test division with scalar (Int)`() {
        val vector = Vec2d(6.0, 8.0)
        val result = vector / 2

        assertEquals(Vec2d(3.0, 4.0), result)
    }

    @Test
    fun `test division with another Vec2d`() {
        val vector1 = Vec2d(6.0, 8.0)
        val vector2 = Vec2d(2.0, 4.0)
        val result = vector1 / vector2

        assertEquals(Vec2d(3.0, 2.0), result)
    }

    @Test
    fun `test round to Int`() {
        val vector = Vec2d(3.5, 4.5)
        val result = vector.roundToInt()

        assertEquals(Vec2d(4.0, 5.0), result)
    }

    @Test
    fun `test Vec2d constants`() {
        assertEquals(Vec2d.ZERO, Vec2d(0.0, 0.0))
        assertEquals(Vec2d.ONE, Vec2d(1.0, 1.0))
        assertEquals(Vec2d.LEFT, Vec2d(-1.0, 0.0))
        assertEquals(Vec2d.RIGHT, Vec2d(1.0, 0.0))
        assertEquals(Vec2d.TOP, Vec2d(0.0, -1.0))
        assertEquals(Vec2d.BOTTOM, Vec2d(0.0, 1.0))
    }
}
