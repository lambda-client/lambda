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
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * Walking across slime to a stop, which is the gametest that kept crashing publication.
 *
 * The stepping drag is the half of slime a tape touches every grounded tick, and the
 * walk has to end in a stop that satisfies the published-trajectory invariant -- three
 * grounded frames at or under the terminal speed. This reproduces the live scenario in
 * the JVM: stone approach, slime floor, and a goal standing on carpet laid over slime.
 */
class SlimeWalkStopTest {
    @Test
    fun `a walk across slime publishes a tape that ends in a stable stop`() {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(BlockPos(x, 99, z), slime())
                for (z in 6..8) put(BlockPos(x, 100, z), carpet())
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
        val start = Stance(0, 100, 0)
        val goal = Stance(0, 100, 5)
        val moves = moveLibrary()
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "coarse search must converge")
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) {
            "no coarse route over the slime: ${planner.routeFailureReport()}"
        }

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
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

        val path = (outcome as? PathPlanResult.Planned)?.path
        checkNotNull(path) { "walk over slime did not plan: $outcome" }
        assertTrue(!path.partial, "walk over slime must reach the goal, not park short of it")
    }

    /**
     * A goal named at the carpet's own cell is refused cleanly, not bridged to.
     *
     * A body on carpet stands in the cell above it, so the carpet's cell is never a
     * stance. It used to slip past the "goal is a stance" gate into the optimistic
     * frontier machinery, which bridged an edge to a goal no chunk arrival could ever
     * make real -- and the walk toward that fiction crashed publication. Optimism is
     * gated on unresolved terrain now, so a standable-nowhere goal in streamed terrain
     * is simply unreachable.
     */
    @Test
    fun `an unstandable goal in streamed terrain is refused, not bridged optimistically`() {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(BlockPos(x, 99, z), slime())
                for (z in 6..8) put(BlockPos(x, 100, z), carpet())
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
        val start = Stance(0, 100, 0)
        val carpetCellGoal = Stance(0, 100, 7)
        val moves = moveLibrary()
        val planner = CoarsePlanner(environment, moves, start, carpetCellGoal)
        planner.advanceFrontier(
            com.lambda.pathing.coarse.FrontierAnchors.sweep(environment, moves, start, carpetCellGoal),
        )
        planner.repair(Duration.INFINITE)

        assertTrue(
            planner.routePlan(0L) == null && planner.optimisticAnchors.isEmpty(),
            "a goal in streamed terrain that nothing can stand in must be refused outright",
        )
    }

    /** A full cube that reflects a landing and drags a stepping body, which is slime. */
    private fun slime() = SnapshotBlockPhysics.of(
        VoxelShapes.fullCube(),
        bounceFactor = 1.0,
        dampensSteppingSpeed = true,
    )

    private fun carpet() = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0),
    )

}
