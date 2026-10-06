/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

@Tag("bedrock-corpus")
class DiagonalZigZagTest {
    @Test
    fun `a diagonal zig zag of lone blocks walks end to end`() {
        val pads = listOf(
            Stance(0, 100, 0), Stance(3, 100, 3), Stance(6, 100, 0),
            Stance(9, 100, 3), Stance(12, 100, 0), Stance(15, 100, 3),
        )
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-4, 90, -4, 20, 112, 8),
            blocks = pads.associate { BlockPos(it.x, it.y - 1, it.z) to SnapshotBlockPhysics.FULL_CUBE },
        )
        val start = pads.first()
        val goal = pads.last()
        val moves = moveLibrary(SimpleMoveOptions())
        val planner = CoarsePlanner(environment, moves, start, goal)
        check(planner.repair(Duration.INFINITE).converged) { "coarse must converge" }
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { "coarse route must exist" }

        val dx = (goal.x - start.x).toDouble()
        val dz = (goal.z - start.z).toDouble()
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val clock = VirtualSearchClock()
        val executor = VirtualExecutor(clock)
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { executor.cursorFrame() },
            publish = { path, _ -> executor.offer(path) },
            started = System.currentTimeMillis(),
            clock = clock,
            adoptedSequence = executor::adoptedSequence,
        )
        val path = (outcome as? PathPlanResult.Planned)?.path
        kotlin.test.assertTrue(
            path != null && !path.partial,
            "the diagonal zig-zag must walk end to end: outcome=${outcome::class.simpleName}",
        )
    }
}
