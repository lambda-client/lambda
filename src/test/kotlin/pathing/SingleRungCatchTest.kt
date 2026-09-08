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
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The field geometry of the 7 Sep 2026 parkour dump: a one-block pillar, a two-block gap
 * three deep, a pillar with a single ladder rung one block up on its near face. From rest
 * the only way on is to catch that rung and climb onto the pillar top.
 * See docs/decisions/movement-tuning.md (ladder catches are catches, not landings).
 */
class SingleRungCatchTest {
    private fun environment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        // Floor three below the launch pad, so a missed catch is a harmful fall.
        for (x in -6..6) for (z in -10..6) blocks[BlockPos(x, 65, z)] = SnapshotBlockPhysics.FULL_CUBE
        // Launch pad: a lone block, stance (0, 69, 0).
        for (y in 66..68) blocks[BlockPos(0, y, 0)] = SnapshotBlockPhysics.FULL_CUBE
        // The pillar at z = -3 up to y = 70 (stance 71), with a landing deck behind it.
        for (y in 66..70) blocks[BlockPos(0, y, -3)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -1..1) for (z in -8..-4) for (y in 66..70) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        // One ladder rung on the pillar's near face, in the gap cell z = -2 at y = 70.
        blocks[BlockPos(0, 70, -2)] = RUNG
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 60, -16, 16, 90, 16), blocks,
        )
    }

    @Test
    fun `a single rung is caught from a standing start and climbed out on top`() {
        catchFrom(Vec3d(0.5, 69.0, 0.5))
    }

    @Test
    fun `a stopped body at the front edge retains a reachable catch gait`() {
        // Translated from plan-1788868411989.dump: no runway remains before launch.
        catchFrom(Vec3d(0.406441704596475, 69.0, 0.0736899258351))
    }

    private fun catchFrom(position: Vec3d) {
        val environment = environment()
        val moves = moveLibrary(SimpleMoveOptions(allowClimbing = true))
        val planner = CoarsePlanner(environment, moves, Stance(0, 69, 0), Stance(0, 71, -6))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.LADDER_CATCH },
            "the rung is the only way across; got ${route.edges.map { it.movement }}",
        )

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = position,
            rotation = Rotation(180.0, 0.0),
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
        assertTrue(!path.partial, "must complete the climb")
        val simulator = MovementSimulator(PROFILE, environment, initial)
        for (input in path.plan.tape.asList()) {
            assertTrue(simulator.tryTickMovement(input) is MovementSimulationStepResult.Advanced)
        }
        val terminal = simulator.state.position
        assertTrue(simulator.state.onGround && simulator.state.velocity.horizontalLength() <= MotionConstraints().stoppedSpeed)
        println("[single-rung] start=$position certified=${path.plan.tape.frameCount}")
        assertTrue(terminal.y > 70.5 && terminal.z < -3.5, "the tape must end on the deck, ended at $terminal")
    }

    private companion object {
        /** A vanilla ladder hung on the -z face of its cell: a 3/16 plank the body collides with. */
        val RUNG = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 0.1875),
            coarseVoxel = CoarseVoxel.of(Medium.CLIMBABLE),
        )
    }
}
