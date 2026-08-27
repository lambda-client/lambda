package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.trajectory.VirtualSearchClock
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

/**
 * The certified tape of a walk that jumps onto slime must carry the reflection.
 *
 * The certification replay runs through the tracking view, and that wrapper once
 * delegated every physics read except the bounce factor and the stepping drag -- so
 * certification simulated slime as dead stone: the search's body reflected a landing
 * the replay's body died on, and publication crashed on its own stable-stop invariant.
 */
class SlimeJumpTapeTest {
    @Test
    fun `a planned jump landing on slime reflects in the certified tape`() {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(
                    BlockPos(x, 99, z),
                    SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true),
                )
                for (z in 6..8) put(
                    BlockPos(x, 100, z),
                    SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)),
                )
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
        val start = Stance(0, 100, 0)
        val goal = Stance(0, 101, 7)
        val moves = SimpleMoveLibrary.build(CoarseMoveCosts.measured(transitionOverheadTicks = 1.0))
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 100.0, 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val clock = VirtualSearchClock()
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { clock.cursorFrame() },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
            clock = clock,
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must reach the goal on the carpet")
        assertTrue(
            path.plan.frames.any { it.state.onGround && it.state.velocity.y > 0.2 },
            "a jump landing on slime must reflect in the certified tape",
        )
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
