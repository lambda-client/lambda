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
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The parkour move the vocabulary never had: jump a one-block gap INTO a ladder hung
 * on a pillar face, climb it, and exit onto the pillar top. Jump templates demand a
 * standable landing, so the mid-wall ladder cell was never a legal target -- the graph
 * could stand there (climbing occupies climbable cells) and the simulator could grab,
 * but no edge crossed the gap. [LadderCatchMovement] is that edge.
 */
class LadderCatchTest {
    private fun environment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        // Launch pad: stance (0, 10, 0).
        for (x in -1..1) for (z in -1..0) blocks[BlockPos(x, 9, z)] = SnapshotBlockPhysics.FULL_CUBE
        // The gap at z = 1 falls into the void. The pillar is the column at z = 3,
        // with the ladder hung on its face in the z = 2 cells.
        for (y in 5..13) blocks[BlockPos(0, y, 3)] = SnapshotBlockPhysics.FULL_CUBE
        for (y in 6..14) blocks[BlockPos(0, y, 2)] = LADDER
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
    }

    private fun moves() = SimpleMoveLibrary.build(
        CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
        SimpleMoveOptions(allowClimbing = true),
    )

    @Test
    fun `a gap onto a ladder routes as a catch and climbs out on top`() {
        val environment = environment()
        val planner = CoarsePlanner(environment, moves(), Stance(0, 10, 0), Stance(0, 14, 3))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.LADDER_CATCH },
            "the gap is only crossable by catching the ladder; got ${route.edges.map { it.movement }}",
        )
        assertTrue(
            route.edges.any { it.movement == MovementId.CLIMB },
            "the pillar top is only reachable by climbing; got ${route.edges.map { it.movement }}",
        )
    }

    @Test
    fun `the catch is certified end to end`() {
        val environment = environment()
        val planner = CoarsePlanner(environment, moves(), Stance(0, 10, 0), Stance(0, 14, 3))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 10.0, 0.5),
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
        assertTrue(!path.partial, "the walk must arrive on the pillar top")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.y > 13.5 && terminal.z > 2.4,
            "the tape must end on the pillar top, ended at $terminal",
        )
    }

    private companion object {
        val LADDER = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            coarseVoxel = CoarseVoxel.of(Medium.CLIMBABLE),
        )

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
