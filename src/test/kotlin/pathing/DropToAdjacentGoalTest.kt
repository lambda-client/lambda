package pathing

// The refusal signature from live gametests: a successor leg rooted on a ledge that
// must drop ~3 blocks to a goal adjacent to the landing, reaching within centimeters
// over hundreds of attempts but never certifying a stable stop.
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.trajectory.MotionPlanResult
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

class DropToAdjacentGoalTest {
    @Test
    fun `a three block drop to the adjacent goal certifies`() {
        val blocks = buildMap {
            // The arena-deck remnant: a one-block-wide stone ledge at y=99.
            for (z in -8..8) put(BlockPos(8, 99, z), SnapshotBlockPhysics.FULL_CUBE)
            // The landing deck three below, stance y=97, holding the goal.
            for (x in 8..11) for (z in -2..2) put(BlockPos(x, 96, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-2, 90, -10, 16, 110, 10), blocks,
        )
        val moves = moveLibrary()
        val start = Stance(8, 100, -1)
        val goal = Stance(9, 97, 0)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { "no coarse route" }
        println("[drop] route=${route.nodes.map { "${it.x},${it.y},${it.z}" }}")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(8.5, 100.0, -0.5),
                rotation = Rotation(-100.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        if (outcome is MotionPlanResult.NoSafeStop) {
            println("[drop] NoSafeStop attempts=${outcome.attemptCount} nearest=${outcome.nearest}")
        }
        assertTrue(outcome is PathPlanResult.Planned, "expected a certified tape, got $outcome")
    }

}
