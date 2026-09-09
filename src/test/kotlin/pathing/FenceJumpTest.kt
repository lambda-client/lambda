/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.actions.LaunchTrigger
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The field jump this pins down: standing on a FENCE (feet a half block above the
 * stance grid), a three-cell air gap, landing on a full block half a block below
 * the feet. The stance delta reads span-4 / drop-1; the REAL flight is a
 * half-block descent from a quarter-block-wide launch pad no run-up fits on.
 *
 * Also carries the drop-reach ground-truth matrix: how far a standing start
 * genuinely clears as the landing gets lower. Straight span-5 (a four-cell air
 * gap) stays out of reach at every drop -- the flight crosses the landing plane
 * two-thirds of a block short -- so the drop-extended template ceiling stops at
 * the measured air gap of 4.0 and only with half a block of real descent in hand.
 */
class FenceJumpTest {
    private val fence = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
        fenceLike = true,
    )
    private val bottomSlab = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
    )

    /**
     * Ground truth for the drop-extended reach: gaps from a standing start on the
     * launch block (delays 0..4 run from the cell centre, never off the block), with
     * the landing progressively lower in REAL height. The static template ceiling is
     * set from this matrix -- measured clusters, not derived numbers.
     */
    @Test
    @Tag("bedrock-corpus")
    fun `standing reach ground truth across real drops`() {
        data class Case(
            val label: String,
            val dx: Int,
            val dz: Int,
            val landing: SnapshotBlockPhysics,
            val landingCell: Int,
        )
        val cases = buildList {
            for ((dx, dz) in listOf(4 to 0, 5 to 0, 5 to 1, 5 to 2, 4 to 4, 6 to 0)) {
                add(Case("drop 0.0", dx, dz, SnapshotBlockPhysics.FULL_CUBE, 100))
                add(Case("drop 0.5", dx, dz, bottomSlab, 100))
                add(Case("drop 1.0", dx, dz, SnapshotBlockPhysics.FULL_CUBE, 99))
                add(Case("drop 1.5", dx, dz, bottomSlab, 99))
                add(Case("drop 2.0", dx, dz, SnapshotBlockPhysics.FULL_CUBE, 98))
            }
        }
        for (case in cases) {
            val from = Stance(0, 101, 0)
            val environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(-8, 80, -8, 16, 120, 16),
                buildMap {
                    put(BlockPos(0, 100, 0), SnapshotBlockPhysics.FULL_CUBE)
                    put(BlockPos(case.dx, case.landingCell, case.dz), case.landing)
                    put(BlockPos(case.dx + 1, case.landingCell, case.dz), case.landing)
                },
            )
            val landingFeet = case.landingCell + 1.0 +
                environment.surfaceOffset(case.dx, case.landingCell, case.dz)
            val realRise = landingFeet - 101.0
            val to = Stance(case.dx, case.landingCell + 1, case.dz)
            val solutions = LaunchSolver.solve(from, to, rise = realRise)
            var certified = 0
            var tried = 0
            for (solution in solutions) {
                for (delay in 0..4) {
                    tried++
                    if (landsOn(environment, from, 101.0, to, landingFeet, solution, delay)) certified++
                }
            }
            val airGap = Math.hypot(
                (case.dx - 1).coerceAtLeast(0).toDouble(),
                (case.dz - 1).coerceAtLeast(0).toDouble(),
            )
            println(
                "[drop-reach] (%d,%d) air=%.2f %s realRise=%.2f solutions=%d certified=%d/%d".format(
                    case.dx, case.dz, airGap, case.label, realRise, solutions.size, certified, tried,
                ),
            )
        }
    }

    /** The exact field jump, solver + rollout: fence launch, 3-air-cell gap, landing 0.5 lower. */
    @Test
    fun `a standing fence launch clears the four gap onto the lower block`() {
        val from = Stance(0, 101, 0)
        val to = Stance(4, 100, 0)
        val environment = fenceWorld()
        // Fence feet: half a block above the stance grid.
        val launchFeet = 101.0 + environment.surfaceOffset(0, 100, 0)
        val landingFeet = 100.0 + environment.surfaceOffset(4, 99, 0)
        val realRise = landingFeet - launchFeet
        val solutions = LaunchSolver.solve(from, to, rise = realRise)
        val certified = solutions.any { solution ->
            (0..2).any { delay -> landsOn(environment, from, launchFeet, to, landingFeet, solution, delay) }
        }
        assertTrue(
            certified,
            "a standing start must clear the fence four-gap (realRise=$realRise, ${solutions.size} solutions)",
        )
    }

    /** The coarse graph must offer the edge and the search must certify the walk. */
    @Test
    fun `the fence four gap routes and certifies end to end`() {
        val environment = fenceWorld()
        val start = Stance(0, 101, 0)
        val goal = Stance(5, 100, 0)
        val moves = moveLibrary(SimpleMoveOptions())
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 60.0, timeBudget = Duration.INFINITE, maxExpansions = 40_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.JUMP },
            "the gap is only crossable by jumping; got ${route.edges.map { it.movement }}",
        )

        val startFeet = start.y + environment.surfaceOffset(start.x, start.y - 1, start.z)
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, startFeet, start.z + 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive past the gap")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.x > 4.4 && terminal.y > 99.9,
            "the tape must end on the landing platform, ended at $terminal",
        )
    }

    /**
     * The opt-in deep-drop family: a four-cell air gap onto a landing one stance
     * down (real drop 1.0) mints ONLY with [SimpleMoveOptions.allowDeepDropJumps].
     * Default-off is deliberate -- see the option's doc for the measured trade.
     */
    @Test
    fun `the deep drop ceiling is opt-in`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 80, -8, 24, 120, 8),
            buildMap {
                put(BlockPos(0, 100, 0), SnapshotBlockPhysics.FULL_CUBE)
                for (x in 5..7) put(BlockPos(x, 99, 0), SnapshotBlockPhysics.FULL_CUBE)
            },
        )
        val origin = Stance(0, 101, 0)
        val target = Stance(5, 100, 0)
        fun jumpEdges(options: SimpleMoveOptions) = moveLibrary(options)
            .edgesFrom(environment, origin)
            .filter { it.movement == MovementId.JUMP && it.to == target }
        assertTrue(
            jumpEdges(SimpleMoveOptions()).isEmpty(),
            "the four-cell drop gap must stay unoffered by default",
        )
        assertTrue(
            jumpEdges(SimpleMoveOptions(allowDeepDropJumps = true)).isNotEmpty(),
            "with deep drops on, the four-cell drop gap must mint a jump edge",
        )
    }

    /** Fence at (0,99,0): stance (0,101,0), feet 100.5. Landing full blocks top 100.0. */
    private fun fenceWorld(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            put(BlockPos(0, 99, 0), fence)
            // Support pillar under the fence so nothing reads as floating.
            put(BlockPos(0, 98, 0), SnapshotBlockPhysics.FULL_CUBE)
            for (x in 4..8) for (z in -1..1) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 80, -8, 24, 120, 8), blocks,
        )
    }

    private fun landsOn(
        environment: SnapshotSimulationEnvironment,
        from: Stance,
        launchFeet: Double,
        to: Stance,
        landingFeet: Double,
        solution: com.lambda.pathing.launch.LaunchSolution,
        delay: Int,
    ): Boolean {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val yaw = Math.toDegrees(Math.atan2(-dx, dz))
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(from.x + 0.5, launchFeet, from.z + 0.5),
            rotation = Rotation(yaw, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val program = SegmentFollowerProgram(
            nodes = listOf(from, to).map { it.center() },
            sprint = solution.sprint,
            lookAheadNodes = 1,
            launch = LaunchTrigger(delay),
            maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
            holdForwardInFlight = solution.holdForward,
            holdTicks = solution.holdTicks,
        )
        val rollout = TrajectoryRolloutEngine.rollout(initial, PROFILE, environment, program, frameCount = 40)
        return rollout.frames.any { frame ->
            frame.state.onGround && frame.index > delay &&
                Math.abs(frame.state.position.y - landingFeet) < 0.01 &&
                frame.state.position.x > to.x - 0.3 && frame.state.position.x < to.x + 1.3 &&
                frame.state.position.z > to.z - 0.3 && frame.state.position.z < to.z + 1.3
        }
    }

}
