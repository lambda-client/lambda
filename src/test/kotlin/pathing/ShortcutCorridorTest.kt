import com.lambda.pathing.refinement.ShortcutCorridor
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.worldview.BlockTraits
import com.lambda.worldview.WorldView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MC-free tests for the exact any-angle corridor validator. */
class ShortcutCorridorTest {
    private val air = BlockTraits(passable = true, centerPassable = true, standableFullTop = false, collisionTopBlips = 0, exact = true)
    private val stone = BlockTraits(passable = false, centerPassable = false, standableFullTop = true, collisionTopBlips = 16, exact = true)

    private inner class FlatWorld : WorldView {
        val overrides = HashMap<FastVector, BlockTraits>()

        override fun stateId(x: Int, y: Int, z: Int): Int = 0

        override fun traits(x: Int, y: Int, z: Int): BlockTraits =
            overrides[fastVectorOf(x, y, z)] ?: if (y == 0) stone else air
    }

    @Test
    fun `clear flat corridor accepts without sampling`() {
        val world = FlatWorld()

        assertNull(
            ShortcutCorridor.firstBlockedColumn(
                view = world,
                x0 = 0.5,
                z0 = 0.5,
                x1 = 8.5,
                z1 = 3.5,
                y = 1,
                halfWidth = 0.35,
            )
        )
    }

    @Test
    fun `corridor reports a blocking feet column touched by the swept footprint`() {
        val world = FlatWorld().apply {
            overrides[fastVectorOf(3, 1, 3)] = stone
        }

        assertEquals(
            fastVectorOf(3, 1, 3),
            ShortcutCorridor.firstBlockedColumn(
                view = world,
                x0 = 0.5,
                z0 = 0.5,
                x1 = 5.5,
                z1 = 5.5,
                y = 1,
                halfWidth = 0.35,
            )
        )
    }

    @Test
    fun `corridor reports a support hole under the swept footprint`() {
        val world = FlatWorld().apply {
            overrides[fastVectorOf(2, 0, 0)] = air
        }

        assertEquals(
            fastVectorOf(2, 1, 0),
            ShortcutCorridor.firstBlockedColumn(
                view = world,
                x0 = 0.5,
                z0 = 0.5,
                x1 = 4.5,
                z1 = 0.5,
                y = 1,
                halfWidth = 0.35,
            )
        )
    }

    @Test
    fun `center-passable partial block still blocks the swept corridor`() {
        // Open-fence-gate trait class: the grid planner may walk through its
        // center, but an off-center swept footprint collides with it.
        val openFenceGate = BlockTraits(passable = false, centerPassable = true, standableFullTop = false, collisionTopBlips = 24, exact = true)
        val world = FlatWorld().apply {
            overrides[fastVectorOf(3, 1, 3)] = openFenceGate
        }

        assertEquals(
            fastVectorOf(3, 1, 3),
            ShortcutCorridor.firstBlockedColumn(
                view = world,
                x0 = 0.5,
                z0 = 0.5,
                x1 = 5.5,
                z1 = 5.5,
                y = 1,
                halfWidth = 0.35,
            )
        )
    }

    @Test
    fun `segment touch test excludes parallel columns outside the inflated footprint`() {
        assertTrue(ShortcutCorridor.segmentTouchesColumn(0.5, 0.5, 4.5, 0.5, 2, 0, 0.35))
        assertFalse(ShortcutCorridor.segmentTouchesColumn(0.5, 0.5, 4.5, 0.5, 2, 1, 0.35))
    }
}
