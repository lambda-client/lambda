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

import com.lambda.util.math.CENTER
import com.lambda.util.math.DOWN
import com.lambda.util.math.MathUtils.sq
import com.lambda.util.math.UP
import com.lambda.util.math.dist
import com.lambda.util.math.distSq
import com.lambda.util.math.div
import com.lambda.util.math.minus
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.math.vec3d
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.sqrt
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

class Vec3dTest {

    @Test
    fun `test dist with another Vec3d`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3d(4.0, 5.0, 6.0)
        val result = vector1 dist vector2

        val expected = sqrt((1.0 - 4.0).sq + (2.0 - 5.0).sq + (3.0 - 6.0).sq)
        assertEquals(expected, result)
    }

    @Test
    fun `test dist with Vec3i`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3i(4, 5, 6)
        val result = vector1 dist vector2

        val expected = sqrt((1.0 - 4).sq + (2.0 - 5).sq + (3.0 - 6).sq)
        assertEquals(expected, result)
    }

    @Test
    fun `test distSq with another Vec3d`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3d(4.0, 5.0, 6.0)
        val result = vector1 distSq vector2

        val expected = (1.0 - 4.0).sq + (2.0 - 5.0).sq + (3.0 - 6.0).sq
        assertEquals(expected, result)
    }

    @Test
    fun `test distSq with Vec3i`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3i(4, 5, 6)
        val result = vector1 distSq vector2

        val expected = (1.0 - 4).sq + (2.0 - 5).sq + (3.0 - 6).sq
        assertEquals(expected, result)
    }

    @Test
    fun `test plus with another Vec3d`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3d(4.0, 5.0, 6.0)
        val result = vector1 + vector2

        assertEquals(Vec3d(5.0, 7.0, 9.0), result)
    }

    @Test
    fun `test plus with Vec3i`() {
        val vector1 = Vec3d(1.0, 2.0, 3.0)
        val vector2 = Vec3i(4, 5, 6)
        val result = vector1 + vector2

        assertEquals(Vec3d(5.0, 7.0, 9.0), result)
    }

    @Test
    fun `test plus with scalar (Double)`() {
        val vector = Vec3d(1.0, 2.0, 3.0)
        val result = vector + 2.0

        assertEquals(Vec3d(3.0, 4.0, 5.0), result)
    }

    @Test
    fun `test minus with another Vec3d`() {
        val vector1 = Vec3d(5.0, 7.0, 9.0)
        val vector2 = Vec3d(4.0, 5.0, 6.0)
        val result = vector1 - vector2

        assertEquals(Vec3d(1.0, 2.0, 3.0), result)
    }

    @Test
    fun `test minus with Vec3i`() {
        val vector1 = Vec3d(5.0, 7.0, 9.0)
        val vector2 = Vec3i(4, 5, 6)
        val result = vector1 - vector2

        assertEquals(Vec3d(1.0, 2.0, 3.0), result)
    }

    @Test
    fun `test multiplication with scalar (Double)`() {
        val vector = Vec3d(1.0, 2.0, 3.0)
        val result = vector * 2.0

        assertEquals(Vec3d(2.0, 4.0, 6.0), result)
    }

    @Test
    fun `test multiplication with scalar (Int)`() {
        val vector = Vec3d(1.0, 2.0, 3.0)
        val result = vector * 2

        assertEquals(Vec3d(2.0, 4.0, 6.0), result)
    }

    @Test
    fun `test division with scalar (Double)`() {
        val vector = Vec3d(4.0, 8.0, 12.0)
        val result = vector / 2.0

        assertEquals(Vec3d(2.0, 4.0, 6.0), result)
    }

    @Test
    fun `test division with scalar (Int)`() {
        val vector = Vec3d(4.0, 8.0, 12.0)
        val result = vector / 2

        assertEquals(Vec3d(2.0, 4.0, 6.0), result)
    }

    @Test
    fun `test Vec3i conversion to Vec3d`() {
        val vector = Vec3i(1, 2, 3)
        val result = vector.vec3d

        assertEquals(Vec3d(1.0, 2.0, 3.0), result)
    }

    @Test
    fun `test constants`() {
        assertEquals(Vec3d(0.0, 1.0, 0.0), UP)
        assertEquals(Vec3d(0.0, -1.0, 0.0), DOWN)
        assertEquals(Vec3d(0.5, 0.5, 0.5), CENTER)
    }
}
