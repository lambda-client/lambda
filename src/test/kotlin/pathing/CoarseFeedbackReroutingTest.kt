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
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseEdgeId
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.InputTape
import com.lambda.pathing.trajectory.SimulatedTrajectoryFrame
import com.lambda.pathing.trajectory.TrajectoryRollout
import com.lambda.pathing.trajectory.TrajectoryRolloutTermination
import com.lambda.pathing.trajectory.WalkingSeedParameters
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * M6 negative feedback: when the trajectory layer cannot certify a permissive
 * `JUMP_CANDIDATE`, [TrajectoryPlanner.searchWithRerouting] retires that edge in D* and
 * reroutes instead of failing the whole plan.
 */
class CoarseFeedbackReroutingTest {
    @Test
    fun `an infeasible coarse jump is retired and the plan reroutes around it`() {
        // The only way across the direct line is a span-4 jump over a void; a longer
        // ground detour also exists. D* prefers the jump, so the first route contains it.
        val world = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(4, 0, 0)] = CoarseVoxel.FULL_BLOCK
            // x=1..3 at z=0 stay void, so no walk crosses directly -- only the jump does.
            for (x in 0..4) for (z in 1..2) this[VoxelPos(x, 0, z)] = CoarseVoxel.FULL_BLOCK
        }
        val planner = planner(world, allowDiagonal = false)

        var refusals = 0
        val outcome = assertNotNull(
            TrajectoryPlanner.searchWithRerouting(planner, snapshotRevision = 7L) { route ->
                val jump = route.edges.firstOrNull { it.kind == CoarseMoveKind.JUMP_CANDIDATE }
                if (jump != null) {
                    refusals++
                    WalkingSeedSearchResult.NoSafeStop(attempts = emptyList(), deadEdge = jump)
                } else {
                    success(route)
                }
            }
        )

        assertIs<WalkingSeedSearchResult.Success>(outcome.result)
        assertTrue(outcome.reroutes >= 1, "at least one infeasible jump had to be retired")
        assertEquals(outcome.reroutes, refusals, "each refusal retires exactly one jump, then success")
        assertTrue(
            outcome.route.edges.none { it.kind == CoarseMoveKind.JUMP_CANDIDATE },
            "the rerouted plan must avoid every retired jump: ${outcome.route.edges.map { it.kind }}",
        )
        assertEquals(Stance(4, 1, 0), outcome.route.goal)
    }

    @Test
    fun `a non-jump refusal is returned as-is, never blacklisted`() {
        val world = SyntheticView().apply { fillGround(-4..8, -4..4, y = 0) }
        // Jumps off, so the flat route is walks only and the refused edge is a walk.
        val planner = planner(world, allowDiagonal = false, allowJumpCandidates = false)

        val outcome = assertNotNull(
            TrajectoryPlanner.searchWithRerouting(planner, snapshotRevision = 1L) { route ->
                // A walk that could not certify has no alternative; retiring it would spiral.
                WalkingSeedSearchResult.NoSafeStop(attempts = emptyList(), deadEdge = route.edges.first())
            }
        )

        assertEquals(0, outcome.reroutes)
        assertIs<WalkingSeedSearchResult.NoSafeStop>(outcome.result)
    }

    @Test
    fun `rerouting is bounded by maxReroutes`() {
        val world = SyntheticView().apply { fillGround(-4..8, -4..4, y = 0) }
        val planner = planner(world, allowDiagonal = false)

        // A jump the search refuses on every route: the loop must give up at the bound
        // rather than spin forever.
        val stubbornJump = CoarseEdge(
            id = CoarseEdgeId(MotionTemplateId(0), Stance(9, 9, 9)),
            from = Stance(9, 9, 9),
            to = Stance(11, 9, 9),
            kind = CoarseMoveKind.JUMP_CANDIDATE,
            lowerBoundTicks = 12.0,
            readSet = emptySet(),
        )

        val outcome = assertNotNull(
            TrajectoryPlanner.searchWithRerouting(planner, snapshotRevision = 1L, maxReroutes = 3) {
                WalkingSeedSearchResult.NoSafeStop(attempts = emptyList(), deadEdge = stubbornJump)
            }
        )

        assertEquals(3, outcome.reroutes)
        assertIs<WalkingSeedSearchResult.NoSafeStop>(outcome.result)
    }

    private fun planner(
        world: CoarseVoxelView,
        allowDiagonal: Boolean,
        allowJumpCandidates: Boolean = true,
    ): CoarsePlanner {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts(
                cardinalWalk = 4.0,
                diagonalWalk = 5.7,
                stepUp = 8.0,
                walkOff = { depth -> 4.0 + depth },
                jumpCandidate = { span, rise -> 9.0 + span + rise },
            ),
            options = SimpleMoveOptions(allowDiagonal = allowDiagonal, allowJumpCandidates = allowJumpCandidates),
        )
        return CoarsePlanner(world, moves, Stance(0, 1, 0), Stance(4, 1, 0)).also {
            it.repair(timeBudget = Duration.INFINITE)
        }
    }

    private fun success(route: CoarseRoutePlan): WalkingSeedSearchResult.Success {
        val state = MovementSimulationState(
            position = Vec3d(0.5, 1.0, 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d.ZERO,
            boundingBox = Box(0.2, 1.0, 0.2, 0.8, 2.8, 0.8),
            onGround = true,
            isJumping = false,
            isSprinting = false,
            isSneaking = false,
            jumpingCooldown = 0,
            velocityAffectingPos = BlockPos(0, 0, 0),
            horizontalCollision = false,
            collidedSoftly = false,
            verticalCollision = false,
            supportingBlockPos = null,
        )
        return WalkingSeedSearchResult.Success(
            sourceRoute = route,
            tape = InputTape(listOf(MovementSimulationInput())),
            rollout = TrajectoryRollout(
                initialState = state,
                frames = listOf(SimulatedTrajectoryFrame(0, MovementSimulationInput(), state)),
                termination = TrajectoryRolloutTermination.Completed,
            ),
            parameters = WalkingSeedParameters(sprint = false, lookAheadNodes = 1, brakeDistance = 0.25, stepUpJumpLeadDistance = null),
            dependencies = emptySet(),
            attempts = emptyList(),
        )
    }

    private class SyntheticView : CoarseVoxelView {
        private val voxels = HashMap<VoxelPos, CoarseVoxel>()

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
            voxels[VoxelPos(x, y, z)] ?: CoarseVoxel.AIR

        /** Trait-derived shapes so the swept-arc jump mask works on synthetic worlds. */
        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape =
            if (voxel(x, y, z).fullyPassable) VoxelShapes.empty() else VoxelShapes.fullCube()

        operator fun set(pos: VoxelPos, voxel: CoarseVoxel) {
            voxels[pos] = voxel
        }

        fun fillGround(xs: IntRange, zs: IntRange, y: Int) {
            for (x in xs) for (z in zs) voxels[VoxelPos(x, y, z)] = CoarseVoxel.FULL_BLOCK
        }
    }
}
