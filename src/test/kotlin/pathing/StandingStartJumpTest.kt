package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.RefusalRerouter
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * The live parkour flake: a leg boundary settles the body to rest at the lip of a
 * span-3 rise-1 gap. The coarse edge assumes cruise entry speed; a standing start
 * with half a block of run-up cannot reach it, and the walk strands unless the
 * search can synthesize a run-up.
 */
class StandingStartJumpTest {
    @Test
    fun `standing at the lip of a span-3 rise-1 gap still certifies`() {
        certifies(gapWorld(), goal = Stance(3, 2, 0))
    }

    @Test
    fun `standing at the lip of a span-4 gap synthesizes a run-up`() {
        certifies(wideGapWorld(), goal = Stance(4, 1, 0))
    }

    @Test
    fun `a refused route edge is demoted and the walk detours`() {
        val environment = detourWorld()
        val start = Stance(0, 1, 0)
        val goal = Stance(4, 2, 0)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = requireNotNull(planner.routePlan(1L))
        assertTrue(route.nodes.size == 2, "the direct span-4 jump must win the coarse route: ${route.nodes}")

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.93, 1.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val field = planner.valueField()
        val rerouter = RefusalRerouter(
            planner = planner,
            field = field,
            reroute = { planner.routePlan(1L) },
            cancelled = { false },
        )
        val result = rerouter.walk(route) { attempted ->
            TrajectoryPlanner.walkHorizon(
                attempted, planner, initial, PROFILE, environment, MotionConstraints(),
                cursorFrame = { null },
                publish = { _, _ -> },
                started = 0L,
            )
        }
        assertIs<PathPlanResult.Planned>(
            result,
            "demoting the refused direct jump must reroute across the detour pads",
        )
        assertTrue(
            result.path.route.nodes.size > 2,
            "the certified route must be the detour: ${result.path.route.nodes}",
        )
    }

    private fun detourWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // direct line: span-4 rise-1 over void — coarse-visible, hopeless from a stand
            put(BlockPos(4, 1, 0), SnapshotBlockPhysics.FULL_CUBE)
            // detour pads: two short hops and a short rise
            put(BlockPos(2, 0, -2), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(4, 0, -2), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -5, 8, 6, 3),
            blocks,
        )
    }

    private fun certifies(environment: SnapshotSimulationEnvironment, goal: Stance) {
        val start = Stance(0, 1, 0)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "coarse search must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = requireNotNull(planner.routePlan(1L)) { "coarse route must exist" }
        assertTrue(
            route.edges.any { it.launch != null },
            "fixture must route across the gap by launching: ${route.nodes}",
        )

        // Settled standing start AT the lip — where a braking stop actually leaves the
        // body after walking in: past every launch offset, with no run-up left.
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.93, 1.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val result = ValueFieldAnchorSearch.search(
            route, moves.catalog, planner.valueField(), initial, PROFILE, environment,
            MotionConstraints(),
        )
        assertIs<MotionPlanResult.Success>(
            result,
            "a standing start with run-up room behind must certify the gap",
        )
    }

    private fun gapWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            // run-up platform: x in -6..0 at y=0 (stand on y=1)
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // gap at x=1..2 over void; landing is a single pad one block higher
            put(BlockPos(3, 1, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -4, 7, 6, 3),
            blocks,
        )
    }

    private fun wideGapWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // gap at x=1..3 over void; flat landing pad at x=4
            put(BlockPos(4, 0, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -4, 8, 6, 3),
            blocks,
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
