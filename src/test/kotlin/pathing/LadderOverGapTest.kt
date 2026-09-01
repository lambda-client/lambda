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
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The field ladder that paced the bot back and forth: hung on a pillar face with a
 * GAP under its bottom cell (the pit floor two below). Walking in drops the feet
 * below the ladder's bottom cell before the wall-press engages -- vanilla stops
 * counting the body as climbing the moment the FEET cell is not climbable -- so the
 * entry must JUMP from the ground. Covered in both directions: floor-to-top past
 * the hanging bottom, and pillar-top down into the column and out below.
 */
class LadderOverGapTest {
    private fun environment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -1..1) {
            // Approach floor: stance (x, 9, 0..1).
            for (z in 0..1) blocks[BlockPos(x, 8, z)] = SnapshotBlockPhysics.FULL_CUBE
            // Pit floor two below the approach, under the ladder column.
            for (z in 2..3) blocks[BlockPos(x, 6, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        // The pillar at z=3 with the ladder hung on its face at z=2; nothing at
        // (x, 7..8, 2): the ladder's bottom cell y=9 hangs over the pit.
        for (y in 9..12) {
            blocks[BlockPos(0, y, 3)] = SnapshotBlockPhysics.FULL_CUBE
            blocks[BlockPos(0, y, 2)] = LADDER
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 32, 16), blocks,
        )
    }

    private fun moves() = moveLibrary(SimpleMoveOptions(allowClimbing = true))

    private fun certify(start: Stance, goal: Stance, startFeet: Double): Vec3d {
        val environment = environment()
        val planner = CoarsePlanner(environment, moves(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 60.0, timeBudget = Duration.INFINITE, maxExpansions = 40_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, startFeet, start.z + 0.5),
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
        assertTrue(!path.partial, "the walk must arrive")
        return path.plan.frames.last().state.position
    }

    @Test
    fun `a hanging ladder bottom is entered jumping from the floor beside it`() {
        val terminal = certify(Stance(0, 9, 0), Stance(0, 13, 3), startFeet = 9.0)
        assertTrue(
            terminal.y > 12.5 && terminal.z > 2.4,
            "the tape must end on the pillar top, ended at $terminal",
        )
    }

    @Test
    fun `the same ladder is entered from the pillar top and ridden down`() {
        val terminal = certify(Stance(0, 13, 3), Stance(0, 7, 3), startFeet = 13.0)
        assertTrue(
            abs(terminal.y - 7.0) < 0.01 && terminal.z > 1.5,
            "the tape must end on the pit floor, ended at $terminal",
        )
    }

    private companion object {
        val LADDER = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            coarseVoxel = CoarseVoxel.of(Medium.CLIMBABLE),
        )

    }
}
