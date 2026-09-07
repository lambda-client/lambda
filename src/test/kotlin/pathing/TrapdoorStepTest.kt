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
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The field jump the template table refused: a three-gap jump whose landing is a
 * BOTTOM TRAPDOOR. The stance delta reads "span 4, rise 1" -- past the measured
 * reach of a genuinely rising jump, so no template was ever offered -- while the
 * real ascent is 0.19 blocks and flies like a flat jump. The rising reach ceiling
 * must be judged against the surface-corrected rise, not the stance delta.
 */
class TrapdoorStepTest {
    private val trapdoor = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.1875, 1.0),
    )

    private fun world(landing: SnapshotBlockPhysics, landingCellY: Int): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -1..1) {
                for (z in 0..1) put(BlockPos(x, 9, z), SnapshotBlockPhysics.FULL_CUBE)
                // Three-cell gap at z=2..4, then the landing row.
                for (z in 5..7) put(BlockPos(x, landingCellY, z), landing)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
    }

    private fun planner(environment: SnapshotSimulationEnvironment, goal: Stance) = CoarsePlanner(
        environment,
        moveLibrary(SimpleMoveOptions()),
        Stance(0, 10, 1),
        goal,
    )

    @Test
    fun `a three-gap jump onto a bottom trapdoor routes and certifies`() {
        val environment = world(trapdoor, landingCellY = 10)
        val planner = planner(environment, goal = Stance(0, 11, 6))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 40.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 10.0, 1.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive on the trapdoors")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.z > 5.4 && abs(terminal.y - 10.1875) < 0.01,
            "the tape must end standing on the trapdoor, ended at $terminal",
        )
    }

    /**
     * The mirror case, field-regressed once: launching FROM a bottom slab onto a
     * full block reads as stance rise 0 while the body really ascends half a block.
     * A half-block real rise still flies a three-gap; only a full block of rise
     * shortens the reach.
     */
    @Test
    fun `a three-gap jump from a slab lip onto full blocks routes and certifies`() {
        val slab = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
        )
        val blocks = buildMap {
            for (x in -1..1) {
                put(BlockPos(x, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
                // The slab lip: stance (x, 11, 1), feet at 10.5.
                put(BlockPos(x, 10, 1), slab)
                // Three-cell gap at z=2..4, then full blocks: stance y=11, feet 11.0.
                for (z in 5..7) put(BlockPos(x, 10, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        val planner = CoarsePlanner(
            environment,
            moveLibrary(SimpleMoveOptions()),
            Stance(0, 11, 1),
            Stance(0, 11, 6),
        )
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 40.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 10.5, 1.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive on the far blocks")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.z > 5.4 && abs(terminal.y - 11.0) < 0.01,
            "the tape must end standing on the full blocks, ended at $terminal",
        )
    }

    /** The same stance delta onto FULL blocks is a true block of rise: still refused. */
    @Test
    fun `the same jump onto full blocks stays impossible`() {
        val planner = planner(world(SnapshotBlockPhysics.FULL_CUBE, landingCellY = 10), goal = Stance(0, 11, 6))
        planner.repair(Duration.INFINITE)
        assertNull(planner.routePlan(0L), "span 4 rise 1 onto full blocks must not route")
    }

}
