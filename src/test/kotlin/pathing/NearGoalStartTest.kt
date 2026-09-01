package pathing

// A search must be able to BEGIN inside finishing range of the goal: a partial tape
// legally stops within a few blocks of it, and the successor leg roots there.
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
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
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L))

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(2.5, 100.0, 8.5),
                rotation = Rotation(-135.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        assertTrue(outcome is PathPlanResult.Planned, "expected a certified tape, got $outcome")
    }

}
