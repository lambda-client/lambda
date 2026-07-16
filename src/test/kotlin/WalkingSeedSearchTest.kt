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
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseEdgeId
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.core.TailCost
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
    fun `a refusal attributes the actual deeper blocked edge instead of the suffix first edge`() {
        val nodes = listOf(Stance(0, 0, 0), Stance(1, 0, 0), Stance(3, 0, 0))
        val walk = CoarseEdge(
            CoarseEdgeId(MotionTemplateId(0), nodes[0]), nodes[0], nodes[1],
            CoarseMoveKind.WALK, 2.0, emptySet(),
        )
        val jump = CoarseEdge(
            CoarseEdgeId(MotionTemplateId(1), nodes[1]), nodes[1], nodes[2],
            CoarseMoveKind.JUMP_CANDIDATE, 4.0, emptySet(),
        )
        val route = CoarseRoutePlan(
            1L, 1L, nodes, listOf(walk, jump), 6.0, true, emptySet(),
            listOf(
                TailCost.Exact(6.0, 1L),
                TailCost.Bounds(4.0, 4.0, 1L),
                TailCost.Exact(0.0, 1L),
            ),
            SimpleMoveLibrary.HeuristicCaps(2.0, 0.0, 0.0),
        )
        val blocks = buildMap {
            for (x in -3..1) for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in 3..8) for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-3, -4, -3, 8, 5, 3), blocks,
        )

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapJumps = 0),
            ),
        )

        assertEquals(1, result.blockedProgress)
        assertEquals(jump, result.deadEdge)
    }

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
     * A staircase of consecutive one-block rises. Between steps the body is grounded for a
     * single tick, so lead distance cannot help; the only lever is the gait. Sprinting
     * scrapes every riser (it slams the next wall while still rising), so the winner must
     * be chosen on cleanliness, not just frames -- a frame-only pick took the sprint tape
     * for a one-tick gain and bumped up the whole staircase.
     */
    @Test
    fun `a staircase keeps the genuinely faster gait and uses scrape events only as a tie break`() {
        val environment = staircaseEnvironment(steps = 5)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = true,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(7, 5, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 30L))
        assertEquals(5, route.edges.count { it.kind == CoarseMoveKind.STEP_UP })

        val full = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )
        val sprintOnly = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(sprintModes = listOf(true)),
            ),
        )

        fun bumps(result: WalkingSeedSearchResult.Success) =
            result.rollout.frames.count { it.state.horizontalCollision }

        // Reaches the top platform.
        assertTrue(kotlin.math.abs(full.rollout.finalState.position.y - 5.0) <= 0.05)
        assertTrue(hypot(full.rollout.finalState.position.x - 7.5, full.rollout.finalState.position.z - 0.5) <= 0.20)
        // Phase 0 removes the scalar "four ticks per bump" toll. Total travel time is
        // correctness-bearing key 3; contact events are a later lexicographic tie-break,
        // so the one-tick-faster sprint must not be made artificially slower.
        assertTrue(full.parameters.sprint)
        assertEquals(sprintOnly.tape.frameCount, full.tape.frameCount)
        assertEquals(bumps(sprintOnly), bumps(full))
    }

    /**
     * The bump toll is a preference, not a brake on speed: with no walls to scrape, both
     * gaits are equally clean and the faster (sprinting) one must still win.
     */
    @Test
    fun `flat ground still sprints because nothing scrapes`() {
        val environment = flatEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(8, 0, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 31L))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.frames.none { it.state.horizontalCollision })
        assertTrue(result.parameters.sprint, "with nothing to scrape, sprint (fewer frames) must win")
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
     * A rise the body must jump (step-up disabled, so the coarse layer routes it as a
     * `JUMP_CANDIDATE`). The launch is discovered by backtracking from the collision, and
     * frame-minimisation alone put it at the very lip of the wall -- "directly in front,
     * even though it had space to jump earlier." The margin reward must launch it with a
     * stride of runway to spare.
     */
    @Test
    fun `a jumped rise launches with runway to spare, not at the wall`() {
        val environment = wallEnvironment()
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = true,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(9, 1, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 32L))
        assertTrue(route.edges.any { it.kind == CoarseMoveKind.JUMP_CANDIDATE }, "the rise must be jumped")

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        // The wall face is at x=5.0. The first launch must leave the body at least a block
        // of floor ahead of it -- frame-only selection launched at x>4.1 (~0.8 before it).
        val launchFrame = result.tape.asList().indexOfFirst { it.jump }
        assertTrue(launchFrame >= 0, "the wall cannot be reached without a jump")
        val launchX = result.rollout.frames[launchFrame].state.position.x
        assertTrue(launchX <= 4.0, "the rise should launch with runway, not at the wall (x=$launchX)")
        assertTrue(kotlin.math.abs(result.rollout.finalState.position.y - 1.0) <= 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 9.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
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

    @Test
    fun `a refused whole hairpin expands continuously through predicted moving splices`() {
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
        val route = requireNotNull(planner.routePlan(snapshotRevision = 16L))
        val initial = initialState(Rotation(0.0, 0.0))
        val config = WalkingSeedSearchConfig(maxFrames = 35)

        assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(route, initial, PROFILE, environment, config),
        )
        val expanded = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.searchContinuously(route, initial, PROFILE, environment, config),
        )
        val walkOnly = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.searchContinuously(
                route, initial, PROFILE, environment,
                config.copy(sprintModes = listOf(false)),
            ),
        )

        assertTrue(expanded.controlSegments > 1)
        assertTrue(expanded.spliceFrames.all { frame ->
            expanded.rollout.frames[frame - 1].state.velocity.horizontalLength() > config.stoppedSpeed
        })
        val collisions = expanded.rollout.frames.count { it.state.horizontalCollision }
        assertTrue(collisions <= 1, "continuous hairpin must not grind along walls, got $collisions collision frames")
        assertTrue(
            expanded.tape.frameCount < walkOnly.tape.frameCount,
            "the one tolerated scrape must buy real total time: ${expanded.tape.frameCount} vs ${walkOnly.tape.frameCount}",
        )
        assertTrue(hypot(expanded.rollout.finalState.position.x - 4.5, expanded.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `a survivable drop is walked off and certified`() {
        val environment = dropEnvironment(depth = 3)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 3,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(6, -3, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 20L))
        assertTrue(route.edges.any { it.kind == CoarseMoveKind.WALK_OFF }, "the route must drop")

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.finalState.onGround)
        assertTrue(kotlin.math.abs(result.rollout.finalState.position.y - (-3.0)) <= 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    /**
     * The coarse layer proposes a drop from geometry alone -- it knows the shaft is
     * clear, not that the landing is survivable. Only simulation can tell, and it must
     * refuse rather than execute. Safety is a hard gate.
     */
    @Test
    fun `a lethal drop the coarse layer proposed is refused by simulation`() {
        val environment = dropEnvironment(depth = 8)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                // Deliberately optimistic: the graph is allowed to propose an 8-block drop.
                maxWalkOffDepth = 8,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(6, -8, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 21L))
        assertTrue(route.edges.any { it.kind == CoarseMoveKind.WALK_OFF })

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        val diagnostics = result.attempts.mapNotNull { it.diagnostic }
        assertTrue(
            diagnostics.any { it is TrajectoryDiagnostic.HarmfulFall },
            "expected a HarmfulFall refusal, got ${diagnostics.map { it::class.simpleName }.distinct()}",
        )
    }

    /** Floor at y=-1 up to x=2, then the ground drops [depth] blocks for the rest. */
    private fun dropEnvironment(depth: Int): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..2) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (x in 3..9) for (z in -3..3) {
                put(BlockPos(x, -1 - depth, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -4 - depth, -3, 9, 5, 3),
            blocks = blocks,
        )
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

    /** Long flat runway, then a one-block wall/platform from x=5 that must be jumped onto. */
    private fun wallEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..4) for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in 5..12) for (z in -3..3) put(BlockPos(x, 0, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, 12, 6, 3),
            blocks = blocks,
        )
    }

    /** A single-block staircase: step `s` is the block at `(s, s-1)`, then a top platform. */
    private fun staircaseEnvironment(steps: Int): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (z in -3..3) put(BlockPos(0, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            for (s in 1..steps) for (z in -3..3) put(BlockPos(s, s - 1, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in steps + 1..steps + 4) for (z in -3..3) put(BlockPos(x, steps - 1, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -3, -3, steps + 4, steps + 6, 3),
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
