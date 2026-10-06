package pathing

// A search must be able to BEGIN inside finishing range of the goal: a partial tape
// legally stops within a few blocks of it, and the successor leg roots there.
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertTrue
import pathing.ProbeScenarios.moveLibrary

class NearGoalStartTest {
    @Test
    fun `a start two blocks from the goal still certifies a tape`() {
        val blocks = buildMap {
            for (x in -4..4) for (z in -4..12) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 90, -8, 8, 115, 16), blocks,
        )
        val moves = moveLibrary()
        val start = Stance(2, 100, 8)
        val goal = Stance(0, 100, 10)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair().converged)
        planner.expandSearchField()
        val route = checkNotNull(planner.routePlan(0L))

        val outcome = walkFrom(route, planner, resting(Vec3d(2.5, 100.0, 8.5), -135.0), environment)
        assertTrue(outcome is PathPlanResult.Planned, "expected a certified tape, got $outcome")
    }

}
