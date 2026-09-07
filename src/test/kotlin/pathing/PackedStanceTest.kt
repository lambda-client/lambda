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

import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackedStanceTest {
    /** The tie-break the generic planner used for `MomentumStance`. */
    private data class Node(val stance: Stance, val speed: SpeedClass)

    private val oldOrder: Comparator<Node> =
        compareBy({ it.stance.y }, { it.stance.x }, { it.stance.z }, { it.speed })

    private fun sample(random: Random): Node = Node(
        Stance(
            random.nextInt(PackedStance.XZ_RANGE.first, PackedStance.XZ_RANGE.last + 1),
            random.nextInt(PackedStance.Y_RANGE.first, PackedStance.Y_RANGE.last + 1),
            random.nextInt(PackedStance.XZ_RANGE.first, PackedStance.XZ_RANGE.last + 1),
        ),
        SpeedClass.entries[random.nextInt(2)],
    )

    private fun pack(node: Node): Long = PackedStance.pack(node.stance, node.speed)

    @Test
    fun `round trip over the full ranges including every extreme`() {
        val extremesXZ = listOf(PackedStance.XZ_RANGE.first, -1, 0, 1, PackedStance.XZ_RANGE.last)
        val extremesY = listOf(PackedStance.Y_RANGE.first, -1, 0, 1, PackedStance.Y_RANGE.last)
        for (x in extremesXZ) for (y in extremesY) for (z in extremesXZ) for (speed in SpeedClass.entries) {
            val packed = PackedStance.pack(x, y, z, speed)
            assertTrue(packed >= 0L, "bit 63 must be clear for ($x, $y, $z, $speed)")
            assertEquals(x, PackedStance.unpackX(packed))
            assertEquals(y, PackedStance.unpackY(packed))
            assertEquals(z, PackedStance.unpackZ(packed))
            assertEquals(speed, PackedStance.speed(packed))
            assertEquals(speed.ordinal, PackedStance.speedBit(packed))
            assertEquals(Stance(x, y, z), PackedStance.stance(packed))
        }

        val random = Random(20260907)
        repeat(200_000) {
            val node = sample(random)
            val packed = pack(node)
            assertEquals(node.stance, PackedStance.stance(packed))
            assertEquals(node.speed, PackedStance.speed(packed))
        }
    }

    @Test
    fun `natural long order equals the old y x z speed comparator`() {
        val random = Random(7)
        repeat(500_000) {
            val a = sample(random)
            val b = sample(random)
            val expected = Integer.signum(oldOrder.compare(a, b))
            val actual = Integer.signum(pack(a).compareTo(pack(b)))
            assertEquals(expected, actual, "$a vs $b")
        }

        // Adjacent values on every field, including sign changes, in both directions.
        val base = Node(Stance(0, 0, 0), SpeedClass.STOPPED)
        val neighbours = listOf(
            Node(Stance(0, 0, 0), SpeedClass.MOVING),
            Node(Stance(0, 0, 1), SpeedClass.STOPPED),
            Node(Stance(0, 0, -1), SpeedClass.MOVING),
            Node(Stance(1, 0, -5), SpeedClass.STOPPED),
            Node(Stance(-1, 0, 5), SpeedClass.MOVING),
            Node(Stance(-100, 1, 100), SpeedClass.STOPPED),
            Node(Stance(100, -1, -100), SpeedClass.MOVING),
            Node(Stance(PackedStance.XZ_RANGE.last, PackedStance.Y_RANGE.first, PackedStance.XZ_RANGE.last), SpeedClass.MOVING),
            Node(Stance(PackedStance.XZ_RANGE.first, PackedStance.Y_RANGE.last, PackedStance.XZ_RANGE.first), SpeedClass.STOPPED),
        )
        for (other in neighbours) {
            assertEquals(Integer.signum(oldOrder.compare(base, other)), Integer.signum(pack(base).compareTo(pack(other))), "$other")
            assertEquals(Integer.signum(oldOrder.compare(other, base)), Integer.signum(pack(other).compareTo(pack(base))), "$other")
        }
    }

    @Test
    fun `withSpeed and sameStance touch only the speed bit`() {
        val stopped = PackedStance.pack(Stance(12, -3, -77), SpeedClass.STOPPED)
        val moving = PackedStance.withSpeed(stopped, SpeedClass.MOVING)
        assertEquals(SpeedClass.MOVING, PackedStance.speed(moving))
        assertEquals(stopped, PackedStance.withSpeed(moving, SpeedClass.STOPPED))
        assertEquals(PackedStance.stance(stopped), PackedStance.stance(moving))
        assertTrue(PackedStance.sameStance(stopped, moving))
        assertFalse(PackedStance.sameStance(stopped, PackedStance.pack(Stance(12, -3, -76), SpeedClass.STOPPED)))
        assertEquals(stopped + 1, moving)
    }

    @Test
    fun `out of range coordinates are refused`() {
        assertFailsWith<IllegalArgumentException> { PackedStance.pack(0, PackedStance.Y_RANGE.last + 1, 0, SpeedClass.STOPPED) }
        assertFailsWith<IllegalArgumentException> { PackedStance.pack(0, PackedStance.Y_RANGE.first - 1, 0, SpeedClass.STOPPED) }
        assertFailsWith<IllegalArgumentException> { PackedStance.pack(PackedStance.XZ_RANGE.last + 1, 0, 0, SpeedClass.STOPPED) }
        assertFailsWith<IllegalArgumentException> { PackedStance.pack(0, 0, PackedStance.XZ_RANGE.first - 1, SpeedClass.STOPPED) }
    }
}
