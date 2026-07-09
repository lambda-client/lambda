import com.lambda.config.blocks.PlannerConfig
import com.lambda.pathing.movement.WalkingMovementModel
import com.lambda.pathing.primitives.MoveTable
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.BlockTraits
import com.lambda.worldview.WorldView
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WP2 migration insurance: the template-generated graph must equal the
 * reference walking model edge-for-edge on randomized synthetic worlds,
 * across the config matrix, in both search directions. The reference model
 * is the readable spec; the templates are what ships — this test is the
 * proof they are the same function.
 */
class MoveTableDifferentialTest {
    // --- Synthetic trait palette (trait *logic* is under test, not trait
    // computation, so both implementations read identical values) ---
    private val air = BlockTraits(passable = true, centerPassable = true, standableFullTop = false, collisionTopBlips = 0, exact = true)
    private val stone = BlockTraits(passable = false, centerPassable = false, standableFullTop = true, collisionTopBlips = 16, exact = true)
    private val fence = BlockTraits(passable = false, centerPassable = false, standableFullTop = false, collisionTopBlips = 24, exact = true)
    private val slab = BlockTraits(passable = false, centerPassable = false, standableFullTop = false, collisionTopBlips = 8, exact = true)
    private val paneEdge = BlockTraits(passable = false, centerPassable = true, standableFullTop = false, collisionTopBlips = 16, exact = true)

    // Open fence gate: center column walkable through, but posts poke 1.5
    // high — the only vanilla-shaped trait where centerPassable coexists
    // with intrusion into the voxel above. Without it the SLICE condition's
    // intrusion term is untestable.
    private val openGate = BlockTraits(passable = false, centerPassable = true, standableFullTop = false, collisionTopBlips = 24, exact = true)
    private val palette = listOf(stone, fence, slab, paneEdge, openGate)

    private class SyntheticWorld : WorldView {
        val blocks = HashMap<FastVector, BlockTraits>()
        var fallback: BlockTraits? = null

        override fun stateId(x: Int, y: Int, z: Int): Int = 0
        override fun traits(x: Int, y: Int, z: Int): BlockTraits =
            blocks[fastVectorOf(x, y, z)] ?: fallback ?: error("air fallback unset")
    }

    private data class TestConfig(
        override val allowDiagonal: Boolean,
        override val allowVertical: Boolean,
        override val allowJump: Boolean,
        override val maxDropHeight: Int,
        override val computeBudget: Long = 50,
        override val maxPathLength: Int = 1000,
        override val allowManeuverDiscovery: Boolean = false,
    ) : PlannerConfig

    private val configMatrix = listOf(
        TestConfig(allowDiagonal = true, allowVertical = true, allowJump = true, maxDropHeight = 3),
        TestConfig(allowDiagonal = true, allowVertical = true, allowJump = false, maxDropHeight = 1),
        TestConfig(allowDiagonal = false, allowVertical = true, allowJump = true, maxDropHeight = 4),
        TestConfig(allowDiagonal = true, allowVertical = false, allowJump = false, maxDropHeight = 3),
        TestConfig(allowDiagonal = false, allowVertical = false, allowJump = true, maxDropHeight = 1),
    )

    /** Random rough terrain: heightmap + obstacles + ceilings + pits. */
    private fun generateWorld(random: Random): SyntheticWorld {
        val world = SyntheticWorld().apply { fallback = air }
        for (x in -RANGE..RANGE) {
            for (z in -RANGE..RANGE) {
                val height = random.nextInt(-3, 3)
                for (y in -6..height) world.blocks[fastVectorOf(x, y, z)] = stone
                // Obstacles on the surface.
                when (random.nextInt(10)) {
                    0 -> world.blocks[fastVectorOf(x, height + 1, z)] = palette.random(random)
                    1 -> world.blocks[fastVectorOf(x, height + random.nextInt(2, 5), z)] = stone // ceiling
                    2 -> for (y in -6..height) world.blocks.remove(fastVectorOf(x, y, z)) // pit shaft
                }
            }
        }
        return world
    }

    @Test
    fun `successors and predecessors match the reference model on random worlds`() {
        val random = Random(1337)
        repeat(30) { worldIndex ->
            val world = generateWorld(random)
            for (config in configMatrix) {
                val moves = MoveTable.build(config)
                for (x in -PROBE..PROBE) {
                    for (z in -PROBE..PROBE) {
                        for (y in -5..5) {
                            val node = fastVectorOf(x, y, z)
                            assertEquals(
                                WalkingMovementModel.successors(world, node, config),
                                moves.successors(world, node),
                                "successors mismatch at ($x,$y,$z) world=$worldIndex config=$config",
                            )
                            assertEquals(
                                WalkingMovementModel.predecessors(world, node, config),
                                moves.predecessors(world, node),
                                "predecessors mismatch at ($x,$y,$z) world=$worldIndex config=$config",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `derived heuristic caps equal the reference derivation`() {
        for (config in configMatrix) {
            val derived = MoveTable.build(config).caps
            val reference = WalkingMovementModel.heuristicCaps(config)
            assertEquals(reference.minCostPerHorizontalBlock, derived.minCostPerHorizontalBlock, "horizontal, $config")
            assertEquals(reference.minCostPerAscendedBlock, derived.minCostPerAscendedBlock, "ascended, $config")
            assertEquals(reference.minCostPerDescendedBlock, derived.minCostPerDescendedBlock, "descended, $config")
        }
    }

    /**
     * Soundness of the derived invalidation extents: flip one voxel, every
     * node whose successor set changed must be in affectedNodes(voxel).
     * (Exactness is not required — sufficiency is; but the derived set is
     * also far smaller than the old conservative box.)
     */
    @Test
    fun `affectedNodes covers every semantically affected node`() {
        val random = Random(4242)
        val config = configMatrix.first()
        val moves = MoveTable.build(config)

        repeat(20) {
            val world = generateWorld(random)
            val nodes = buildList {
                for (x in -PROBE..PROBE) for (z in -PROBE..PROBE) for (y in -5..5) add(fastVectorOf(x, y, z))
            }
            val before = nodes.associateWith { moves.successors(world, it) }

            val cx = random.nextInt(-PROBE, PROBE + 1)
            val cy = random.nextInt(-4, 5)
            val cz = random.nextInt(-PROBE, PROBE + 1)
            val pos = fastVectorOf(cx, cy, cz)
            if (world.blocks.containsKey(pos)) world.blocks.remove(pos) else world.blocks[pos] = stone

            val affected = moves.affectedNodes(cx, cy, cz)
            for (node in nodes) {
                if (moves.successors(world, node) != before[node]) {
                    assertTrue(
                        node in affected,
                        "node ${node.str()} changed after flipping ($cx,$cy,$cz) but is not in affectedNodes",
                    )
                }
            }
        }
    }

    private fun FastVector.str(): String = "($x,$y,$z)"

    private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]

    companion object {
        private const val RANGE = 8

        // Probe interior nodes only: moves reach up to 2 horizontally, so
        // staying 2 inside RANGE keeps every read inside generated terrain.
        private const val PROBE = RANGE - 2
    }
}
