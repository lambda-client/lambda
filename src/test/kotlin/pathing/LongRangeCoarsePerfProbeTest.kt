package pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.measureTime

/**
 * Prints where a cold long-range coarse plan spends its work; asserts nothing.
 * Run via bedrockCorpus after touching edge generation or the D* core.
 */
@Tag("bedrock-corpus")
class LongRangeCoarsePerfProbeTest {
    private class CountingView(private val backing: CoarseVoxelView) : CoarseVoxelView {
        var voxelReads = 0L
        var shapeReads = 0L
        var classReads = 0L

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
            voxelReads++
            return backing.voxel(x, y, z)
        }

        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? {
            shapeReads++
            return backing.collisionShape(x, y, z)
        }

        override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
            classReads++
            return backing.collisionClass(x, y, z)
        }

        override fun isKnown(x: Int, y: Int, z: Int): Boolean = backing.isKnown(x, y, z)

        override val simulableStanceY: IntRange get() = backing.simulableStanceY

        fun reset() { voxelReads = 0; shapeReads = 0; classReads = 0 }
    }

    @Test
    fun probe() {
        val length = 400
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                length + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = BedrockFieldLayout.solidCells(length = length).associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val (fromPoint, toPoint) = BedrockFieldLayout.randomEndpointPairs(
            count = 1, length = length, minHorizontalDistance = length * 0.85,
        ).first()
        val start = Stance(fromPoint.x, fromPoint.y, fromPoint.z)
        val goal = Stance(toPoint.x, toPoint.y, toPoint.z)
        println("[range] $start -> $goal (${length} field)")

        for (jumps in listOf(true, false)) {
            val options = SimpleMoveOptions(allowJumpCandidates = jumps, maxJumpDrop = 2)
            var library: SimpleMoveLibrary
            val buildTime = measureTime {
                library = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0), options)
            }
            val view = CountingView(environment)
            val planner = CoarsePlanner(view, library, start, goal)

            val converge = measureTime {
                check(planner.repair(timeBudget = kotlin.time.Duration.INFINITE).converged)
            }
            val convergeReads = Triple(view.voxelReads, view.shapeReads, view.classReads)
            view.reset()

            val fieldTime = measureTime {
                planner.expandField(extraTicks = 36.0, maxExpansions = 200_000)
            }
            val fieldReads = Triple(view.voxelReads, view.shapeReads, view.classReads)
            view.reset()

            var routeLen = -1
            val extractTime = measureTime {
                routeLen = planner.routePlan(1L)?.nodes?.size ?: -1
            }
            println(
                "[range] jumps=$jumps templates=${library.templates.size} build=$buildTime " +
                    "converge=$converge (voxel=${convergeReads.first} shape=${convergeReads.second} class=${convergeReads.third}) " +
                    "nodes=${planner.graphSize} field=$fieldTime (class=${fieldReads.third}) " +
                    "extract=$extractTime routeLen=$routeLen"
            )
        }
    }
}
