import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.worldview.OverlayWorldView
import com.lambda.worldview.WorldView
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WP1 overlay mechanics: fork isolation, commit/rollback semantics, and the
 * invalidation contract (returned change sets cover exactly the voxels whose
 * resolved view may differ). MC-free — WorldView is an interface, so a
 * map-backed synthetic world suffices (harness spec Tier 0).
 */
class OverlayWorldViewTest {
    /** Synthetic base world: sparse map, everything else state 0. */
    private class MapWorld : WorldView {
        val blocks = HashMap<FastVector, Int>()
        override fun stateId(x: Int, y: Int, z: Int): Int = blocks[fastVectorOf(x, y, z)] ?: 0
    }

    @Test
    fun `edit wins over parent and revert exposes parent again`() {
        val base = MapWorld().apply { blocks[fastVectorOf(1, 2, 3)] = 7 }
        val overlay = OverlayWorldView(base)

        assertEquals(7, overlay.stateId(1, 2, 3))
        overlay.edit(fastVectorOf(1, 2, 3), 42)
        assertEquals(42, overlay.stateId(1, 2, 3))
        assertEquals(7, base.stateId(1, 2, 3))

        assertTrue(overlay.revert(fastVectorOf(1, 2, 3)))
        assertEquals(7, overlay.stateId(1, 2, 3))
    }

    @Test
    fun `fork is isolated from the original`() {
        val base = MapWorld()
        val overlay = OverlayWorldView(base)
        overlay.edit(fastVectorOf(0, 64, 0), 5)

        val fork = overlay.fork()
        assertEquals(5, fork.stateId(0, 64, 0))

        fork.edit(fastVectorOf(0, 64, 0), 9)
        fork.edit(fastVectorOf(1, 64, 0), 3)
        assertEquals(5, overlay.stateId(0, 64, 0))
        assertEquals(0, overlay.stateId(1, 64, 0))
        assertEquals(9, fork.stateId(0, 64, 0))
    }

    @Test
    fun `commitTo transfers every edit and empties the source`() {
        val base = MapWorld()
        val active = OverlayWorldView(base)
        val candidate = active.fork()
        candidate.edit(fastVectorOf(2, 60, 2), 11)
        candidate.edit(fastVectorOf(3, 60, 2), 12)

        val changed = candidate.commitTo(active)

        assertEquals(setOf(fastVectorOf(2, 60, 2), fastVectorOf(3, 60, 2)), changed)
        assertEquals(0, candidate.editCount)
        assertEquals(11, active.stateId(2, 60, 2))
        assertEquals(12, active.stateId(3, 60, 2))
    }

    @Test
    fun `rollback reports exactly the edited voxels and restores parent view`() {
        val base = MapWorld().apply { blocks[fastVectorOf(5, 5, 5)] = 1 }
        val overlay = OverlayWorldView(base)
        overlay.edit(fastVectorOf(5, 5, 5), 2)
        overlay.edit(fastVectorOf(6, 5, 5), 3)

        val changed = overlay.rollback()

        assertEquals(setOf(fastVectorOf(5, 5, 5), fastVectorOf(6, 5, 5)), changed)
        assertEquals(0, overlay.editCount)
        assertEquals(1, overlay.stateId(5, 5, 5))
        assertEquals(0, overlay.stateId(6, 5, 5))
    }

    @Test
    fun `randomized - overlay resolution always matches a reference model`() {
        val random = Random(42)
        repeat(50) {
            val base = MapWorld()
            repeat(64) {
                base.blocks[randomPos(random)] = random.nextInt(1, 100)
            }

            val overlay = OverlayWorldView(base)
            val reference = HashMap<FastVector, Int>() // expected overlay-resolved diffs

            repeat(128) {
                val pos = randomPos(random)
                when (random.nextInt(3)) {
                    0 -> {
                        val id = random.nextInt(100)
                        overlay.edit(pos, id)
                        reference[pos] = id
                    }
                    1 -> {
                        overlay.revert(pos)
                        reference.remove(pos)
                    }
                    else -> {
                        val expected = reference[pos] ?: base.blocks[pos] ?: 0
                        assertEquals(expected, overlay.stateId(pos), "at $pos")
                    }
                }
            }

            assertEquals(reference.size, overlay.editCount)

            // Fork + commit round trip preserves resolution everywhere touched.
            val fork = overlay.fork()
            val target = OverlayWorldView(base)
            fork.commitTo(target)
            (reference.keys + base.blocks.keys).forEach { pos ->
                assertEquals(overlay.stateId(pos), target.stateId(pos), "post-commit at $pos")
            }
        }
    }

    private fun randomPos(random: Random): FastVector =
        fastVectorOf(random.nextInt(-8, 8), random.nextInt(0, 8), random.nextInt(-8, 8))
}
