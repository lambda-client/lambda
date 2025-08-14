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

import com.lambda.util.world.X_BITS
import com.lambda.util.world.Z_BITS
import com.lambda.util.world.addX
import com.lambda.util.world.addY
import com.lambda.util.world.addZ
import com.lambda.util.world.distSq
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.offset
import com.lambda.util.world.remainder
import com.lambda.util.world.setX
import com.lambda.util.world.setY
import com.lambda.util.world.setZ
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toVec3d
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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


class FastVectorTest {
    @Test
    fun `test fast vector with valid coordinates`() {
        val x = 123456
        val y = 789
        val z = -12345

        val fastVec = fastVectorOf(x, y, z)

        assertEquals(x, fastVec.x)
        assertEquals(y, fastVec.y)
        assertEquals(z, fastVec.z)
    }

    @Test
    fun `test fast vector with invalid X coordinate`() {
        val x = (1L shl X_BITS - 1)
        val y = 10L
        val z = 20L

        assertFails { fastVectorOf(x, y, z) }
    }

    @Test
    fun `test fast vector with invalid Z coordinate`() {
        val x = 10L
        val y = 20L
        val z = (1L shl Z_BITS - 1)

        assertFails { fastVectorOf(x, y, z) }
    }

    @Test
    fun `test fast vector with Y overflow`() {
        val x = 10L
        val y = 2049L
        val z = 20L

        val fastVec = fastVectorOf(x, y, z)

        assertEquals(-2047, fastVec.y)
    }

    @Test
    fun `test fast vector with Y underflow`() {
        val x = 10L
        val y = -2049L
        val z = 20L

        val fastVec = fastVectorOf(x, y, z)

        assertEquals(2047, fastVec.y)
    }

    @Test
    fun `test setX correctly sets the X coordinate`() {
        var fastVec = fastVectorOf(10, 20, 30)
        fastVec = fastVec setX 40

        assertEquals(40, fastVec.x)
    }

    @Test
    fun `test setY correctly sets the Y coordinate`() {
        var fastVec = fastVectorOf(10, 20, 30)
        fastVec = fastVec setY 50

        assertEquals(50, fastVec.y)
    }

    @Test
    fun `test setZ correctly sets the Z coordinate`() {
        var fastVec = fastVectorOf(10, 20, 30)
        fastVec = fastVec setZ 60

        assertEquals(60, fastVec.z)
    }

    @Test
    fun `test addX correctly adds to the X coordinate`() {
        val fastVec = fastVectorOf(10, 20, 30)
        val newVec = fastVec addX 5

        assertEquals(15, newVec.x)
    }

    @Test
    fun `test addY correctly adds to the Y coordinate`() {
        val fastVec = fastVectorOf(10, 20, 30)
        val newVec = fastVec addY 5

        assertEquals(25, newVec.y)
    }

    @Test
    fun `test addZ correctly adds to the Z coordinate`() {
        val fastVec = fastVectorOf(10, 20, 30)
        val newVec = fastVec addZ 5

        assertEquals(35, newVec.z)
    }

    @Test
    fun `test plus operation with another FastVector`() {
        val vec1 = fastVectorOf(1, 2, 3)
        val vec2 = fastVectorOf(4, 5, 6)

        val result = vec1 + vec2

        assertEquals(5, result.x)
        assertEquals(7, result.y)
        assertEquals(9, result.z)
    }

    @Test
    fun `test minus operation with another FastVector`() {
        val vec1 = fastVectorOf(5, 6, 7)
        val vec2 = fastVectorOf(2, 2, 2)

        val result = vec1 - vec2

        assertEquals(3, result.x)
        assertEquals(4, result.y)
        assertEquals(5, result.z)
    }

    @Test
    fun `test multiplication by scalar`() {
        val vec = fastVectorOf(1, 2, 3)
        val result = vec * 2

        assertEquals(2, result.x)
        assertEquals(4, result.y)
        assertEquals(6, result.z)
    }

    @Test
    fun `test division by scalar`() {
        val vec = fastVectorOf(10, 20, 30)
        val result = vec / 2

        assertEquals(5, result.x)
        assertEquals(10, result.y)
        assertEquals(15, result.z)
    }

    @Test
    fun `test modulo operation with scalar`() {
        val vec = fastVectorOf(10, 20, 30)
        val result = vec remainder 7

        assertEquals(3, result.x)
        assertEquals(6, result.y)
        assertEquals(2, result.z)
    }

    @Test
    fun `test distSq with another FastVector`() {
        val vec1 = fastVectorOf(1, 2, 3)
        val vec2 = fastVectorOf(4, 5, 6)

        val distSq = vec1 distSq vec2

        assertEquals(27.0, distSq)
    }

    @Test
    fun `test offset with Direction`() {
        val vec = fastVectorOf(0, 0, 0)
        val direction = Direction.NORTH // offset: (0, 0, -1)

        val newVec = vec.offset(direction)

        assertEquals(0, newVec.x)
        assertEquals(0, newVec.y)
        assertEquals(-1, newVec.z)
    }

    @Test
    fun `test toBlockPos conversion`() {
        val vec = fastVectorOf(10, 20, 30)
        val blockPos = vec.toBlockPos()

        assertEquals(10, blockPos.x)
        assertEquals(20, blockPos.y)
        assertEquals(30, blockPos.z)
    }

    @Test
    fun `test toVec3d conversion`() {
        val vec = fastVectorOf(10, 20, 30)
        val vec3d = vec.toVec3d()

        assertEquals(10.0, vec3d.x)
        assertEquals(20.0, vec3d.y)
        assertEquals(30.0, vec3d.z)
    }
}
