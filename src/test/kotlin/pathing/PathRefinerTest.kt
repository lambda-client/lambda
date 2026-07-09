import com.lambda.config.blocks.PathRefinementConfig
import com.lambda.pathing.refinement.PathRefiner
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.worldview.BlockTraits
import com.lambda.worldview.WorldView
import kotlin.test.Test
import kotlin.test.assertEquals

/** MC-free tests for the refinement pass on synthetic trait worlds. */
class PathRefinerTest {
    private val air = BlockTraits(passable = true, centerPassable = true, standableFullTop = false, collisionTopBlips = 0, exact = true)
    private val stone = BlockTraits(passable = false, centerPassable = false, standableFullTop = true, collisionTopBlips = 16, exact = true)

    private val config = object : PathRefinementConfig {
        override val enabled = true
        override val clearanceMargin = 0.05
        override val maxLookahead = 64
        override val maxChecks = 2_048
    }

    private inner class FlatWorld : WorldView {
        val overrides = HashMap<FastVector, BlockTraits>()

        override fun stateId(x: Int, y: Int, z: Int): Int = 0

        override fun traits(x: Int, y: Int, z: Int): BlockTraits =
            overrides[fastVectorOf(x, y, z)] ?: if (y == 0) stone else air
    }

    @Test
    fun `collinear walk runs merge into one segment`() {
        val world = FlatWorld()
        val coarse = (0..6).map { fastVectorOf(it, 1, 0) }

        val result = PathRefiner.refine(world, coarse, config)

        assertEquals(listOf(fastVectorOf(0, 1, 0), fastVectorOf(6, 1, 0)), result.path)
    }

    @Test
    fun `collinear simplification never merges across a gap-jump edge`() {
        // Support hole at x=3: the coarse path carries a gap-jump edge
        // (2,1,0) -> (4,1,0). Its takeoff and landing nodes must survive
        // refinement — the executor detects the gap from the 2-block flat
        // segment; merging it into one long walk sends the agent into the
        // hole (the gauntlet-gap1-x4 regression).
        val world = FlatWorld().apply {
            overrides[fastVectorOf(3, 0, 0)] = air
        }
        val coarse = listOf(
            fastVectorOf(0, 1, 0),
            fastVectorOf(1, 1, 0),
            fastVectorOf(2, 1, 0),
            fastVectorOf(4, 1, 0),
            fastVectorOf(5, 1, 0),
            fastVectorOf(6, 1, 0),
        )

        val result = PathRefiner.refine(world, coarse, config)

        assertEquals(
            listOf(
                fastVectorOf(0, 1, 0),
                fastVectorOf(2, 1, 0),
                fastVectorOf(4, 1, 0),
                fastVectorOf(6, 1, 0),
            ),
            result.path,
        )
    }
}
