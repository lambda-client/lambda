/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class WalkingSeedSearchTest {
    @Test
    fun `flat coarse corridor becomes a stopped replayable input tape`() {
        val environment = flatEnvironment()
        val envelope = CoarseKinematicEnvelope(0.6, 0.5, 4.0)
        val moves = SimpleMoveLibrary.build(
            costs = envelope.moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(5, 0, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 9L))
        val initial = initialState()

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initial, PROFILE, environment),
        )

        val final = result.rollout.finalState
        assertTrue(hypot(final.position.x - 5.5, final.position.z - 0.5) <= 0.20)
        assertTrue(final.velocity.horizontalLength() <= 0.012)
        assertTrue(final.onGround)
        assertTrue(result.tape.frameCount > 0)
        assertTrue(result.attempts.isNotEmpty())
        assertTrue(result.dependencies.containsAll(route.dependencies))
        assertTrue(result.dependencies.any { it.y == -1 })

        val replay = MovementSimulator(PROFILE, environment, initial)
        result.tape.asList().forEach { replay.tickMovement(it) }
        assertEquals(final, replay.state)

        val trackedReplay = environment.trackingView()
        TrajectoryRolloutEngine.rollout(initial, PROFILE, trackedReplay, result.tape, result.tape.frameCount)
        assertEquals(route.dependencies + trackedReplay.dependencies(), result.dependencies)
    }

    @Test
    fun `coarse detour is simulated into a collision free corner cut`() {
        val environment = flatEnvironment(
            extraBlocks = mapOf(BlockPos(3, 0, 0) to SnapshotBlockPhysics.FULL_CUBE),
        )
        val envelope = CoarseKinematicEnvelope(0.6, 0.5, 4.0)
        val moves = SimpleMoveLibrary.build(
            costs = envelope.moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(6, 0, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 10L))
        assertTrue(route.nodes.any { it.z != 0 })

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `diagonal coarse corridor becomes a smooth forty five degree trajectory`() {
        val environment = flatEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = true,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(5, 0, 5))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 12L))
        assertTrue(route.nodes.zipWithNext().all { (a, b) -> kotlin.math.abs(b.x - a.x) == 1 && kotlin.math.abs(b.z - a.z) == 1 })

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(hypot(result.rollout.finalState.position.x - 5.5, result.rollout.finalState.position.z - 5.5) <= 0.20)
    }

    @Test
    fun `typed step up searches an early jump and stops on the upper platform`() {
        val environment = stepEnvironment()
        val envelope = CoarseKinematicEnvelope(0.6, 0.5, 4.0)
        val moves = SimpleMoveLibrary.build(
            costs = envelope.moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = true,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(6, 1, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 11L))
        assertTrue(route.edges.any { it.kind == com.lambda.pathing.coarse.CoarseMoveKind.STEP_UP })

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump })
        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(kotlin.math.abs(result.rollout.finalState.position.y - 1.0) <= 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `multiple typed rises preserve landing state and jump the next step`() {
        val environment = twoStepEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = true,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(7, 2, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 13L))
        assertEquals(2, route.edges.count { it.kind == com.lambda.pathing.coarse.CoarseMoveKind.STEP_UP })

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().count { it.jump } >= 2)
        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(kotlin.math.abs(result.rollout.finalState.position.y - 2.0) <= 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 7.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    /**
     * The goal sits on top of the rise, so the brake radius covers the takeoff.
     * Braking on straight-line goal distance suppressed the launch entirely, and
     * a coasting player cannot clear a step-up, so the rise must hold the brake off.
     */
    @Test
    fun `a rise on the final edge still launches inside the brake radius`() {
        val environment = finalRiseEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = true,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(3, 1, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 14L))
        assertEquals(CoarseMoveKind.STEP_UP, route.edges.last().kind)

        // A brake radius wider than the final edge: the takeoff is inside it.
        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(brakeDistances = listOf(1.15)),
            ),
        )

        assertTrue(result.tape.asList().any { it.jump }, "the final rise must be jumped")
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(kotlin.math.abs(result.rollout.finalState.position.y - 1.0) <= 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 3.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    /**
     * A hairpin folds the route back alongside itself. A global nearest-node
     * search snaps the pursuit target across the fold and steers into the wall;
     * progress must only advance along the route.
     */
    @Test
    fun `a hairpin route is followed around the wall rather than through it`() {
        val environment = hairpinEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(4, 0, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 15L))

        // The route must actually fold back alongside itself.
        assertTrue(route.nodes.any { it.z >= 6 }, "the route must go around the wall")

        // The first edge runs +Z, up the outbound leg of the hairpin.
        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(Rotation(0.0, 0.0)), PROFILE, environment),
        )

        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 4.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
        // Rounded the hairpin instead of snapping the pursuit target across the fold.
        assertTrue(result.rollout.frames.any { it.state.position.z >= 6.0 }, "the tape must round the hairpin")
    }

    /** Floor up to x=2, then a one-block rise whose top *is* the goal stance. */
    private fun finalRiseEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..2) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 3..5) for (z in -3..3) {
                put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, 5, 5, 3),
            blocks = blocks,
        )
    }

    /**
     * A wall spanning x=1..3 seals every crossing south of z=6, so start (0,0,0)
     * and goal (4,0,0) sit four blocks apart while the only route runs up to z=6,
     * across, and back down -- folding the return leg alongside the outbound one.
     */
    private fun hairpinEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -2..6) for (z in -2..8) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 1..3) for (z in -2..5) {
                put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-2, -3, -2, 6, 4, 8),
            blocks = blocks,
        )
    }

    private fun flatEnvironment(
        extraBlocks: Map<BlockPos, SnapshotBlockPhysics> = emptyMap(),
    ): SnapshotSimulationEnvironment {
        val floor = buildMap {
            for (x in -3..9) for (z in -3..9) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            putAll(extraBlocks)
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, 9, 4, 9),
            blocks = floor,
        )
    }

    private fun stepEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..2) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 3..9) for (z in -3..3) {
                put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, 9, 5, 3),
            blocks = blocks,
        )
    }

    private fun twoStepEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..1) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 2..3) for (z in -3..3) {
                put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 4..10) for (z in -3..3) {
                put(BlockPos(x, 1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, 10, 6, 3),
            blocks = blocks,
        )
    }

    /**
     * The seed follower always presses forward while it turns, so it cannot pivot
     * in place: the start must face roughly along the first edge. Yaw -90 is +X;
     * yaw 0 is +Z.
     */
    private fun initialState(rotation: Rotation = Rotation(-90.0, 0.0)) = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 0.0, 0.5),
        rotation = rotation,
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
    )

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1,
            sneakSpeedModifier = 0.3,
            gravity = 0.08,
            jumpStrength = 0.42,
            stepHeight = 0.6,
            jumpBoostVelocityModifier = 0.0,
            slowFalling = false,
            width = 0.6,
            height = 1.8,
            eyeHeight = 1.62,
        )
    }
}
