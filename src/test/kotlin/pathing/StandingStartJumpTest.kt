package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.world.CoarseVoxel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

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
    fun `a span-4 rise-1 gap is beyond a standing start and no longer promised`() {
        // The graph used to offer this and rely on the search synthesizing a run-up.
        // Policy reversed 2026-08-27: the coarse graph cannot promise a run-up exists,
        // so it only offers jumps provably reachable from a standing start -- and a
        // rising three-air gap certifies once in nine standing rollouts. Momentum
        // jumps return later as terrain-conditioned templates (runway provably behind
        // the lip) or trajectory-layer shortcuts.
        assertNoRoute(runUpWorld(), goal = Stance(4, 2, 0))
    }

    @Test
    fun `a four-wide gap is not promised by the coarse graph`() {
        assertNoRoute(fourWideWorld(), goal = Stance(5, 1, 0))
    }

    private fun assertNoRoute(environment: SnapshotSimulationEnvironment, goal: Stance) {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 1, 0), goal)
        val converged = planner.repair(Duration.INFINITE).converged
        val route = if (converged) planner.routePlan(0L) else null
        assertTrue(route == null, "a momentum-only jump must not be promised: ${route?.nodes}")
    }

    private fun fourWideWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // four air blocks: the vanilla maximum, only makeable with built momentum
            put(BlockPos(5, 0, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -4, 8, 6, 3),
            blocks,
        )
    }

    @Test
    fun `a settled centre start clears a rise-1 gap onto a one-wide pad`() {
        // The live parkour trap: settled mid-cell (not at the lip) after a hold,
        // facing a span-3 rise-1 edge onto a lone pad. Marginal low-entry arcs
        // arrive at pad height exactly at its near edge and clip the face; the
        // solver must price them infeasible so a run-up gets synthesized instead.
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(3, 1, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -4, 8, 6, 3),
            blocks,
        )
        certifies(environment, goal = Stance(3, 2, 0), initialX = 0.5)
    }

    private fun runUpWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // span-4 rise-1 over void: refused from a stand, makeable with a run-up
            put(BlockPos(4, 1, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -4, 8, 6, 3),
            blocks,
        )
    }

    @Test
    fun `a hopeless route edge is flowed around without touching the coarse graph`() {
        val environment = detourWorld()
        val start = Stance(0, 1, 0)
        val goal = Stance(5, 1, 0)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = requireNotNull(planner.routePlan(1L))
        assertTrue(
            route.nodes.size == 3 && route.nodes[1] == Stance(3, 2, 0),
            "the direct line through the post must win the coarse route: ${route.nodes}",
        )

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 1.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val result = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = 0L,
        )
        val planned = assertIs<PathPlanResult.Planned>(
            result,
            "the walk must certify a detour trajectory over the pads",
        )
        assertTrue(!planned.path.partial, "the detour must reach the goal, not park short")
        assertTrue(
            planned.path.route.nodes.size == 3,
            "the coarse route stays the direct jump — it is a lower bound, not a promise: " +
                "${planned.path.route.nodes}",
        )
        val minZ = planned.path.plan.frames.minOf { it.state.position.z }
        assertTrue(
            minZ < -1.0,
            "the certified trajectory must detour over the pads at z=-2: minZ=$minZ",
        )
    }

    private fun detourWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -6..0) for (z in -2..2) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            // direct line: a span-3 rise-1 hop onto a phantom pad -- the coarse layer
            // reads a standable cell, the collision shape is empty, so every direct
            // arc falls straight through. The mis-capture class live walks meet on
            // streamed terrain, and deterministically hopeless: the trajectory must
            // flow around it while the coarse route keeps believing in it.
            put(
                BlockPos(3, 1, 0),
                SnapshotBlockPhysics(
                    VoxelShapes.empty(),
                    coarseVoxel = CoarseVoxel.FULL_BLOCK,
                ),
            )
            put(BlockPos(5, 0, 0), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(-1, 1, 0), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(-1, 2, 0), SnapshotBlockPhysics.FULL_CUBE)
            // detour pads: two short hops at z=-2 route around the post entirely
            put(BlockPos(2, 0, -2), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(4, 0, -2), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-7, -1, -5, 8, 6, 3),
            blocks,
        )
    }

    private fun certifies(
        environment: SnapshotSimulationEnvironment,
        goal: Stance,
        initialX: Double = 0.93,
    ): MotionPlanResult.Success {
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
            position = Vec3d(initialX, 1.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val result = ValueFieldAnchorSearch.search(
            route, moves.catalog, planner.valueField(), initial, PROFILE, environment,
            MotionConstraints(),
        )
        return assertIs<MotionPlanResult.Success>(
            result,
            "a standing start at the lip must certify the gap",
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
