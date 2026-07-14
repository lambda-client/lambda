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
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import com.lambda.util.player.prediction.UnsupportedPhysics
import com.lambda.util.player.prediction.UnsupportedPhysicsKind
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * M4: a gap jump is *discovered*, not scheduled.
 *
 * The nominal walk falls into the hole. That failure is not noise -- it names the
 * frame the body left the ground, and backtracking over the grounded trace before it
 * finds the launch tick that carries the arc across. Nothing is executed on the
 * strength of the coarse candidate alone: only a full simulation certifies it.
 */
class GapJumpDiscoveryTest {
    /**
     * A one-wide hole needs no jump at all: a sprint carries the body across, falling
     * ~0.4 blocks and auto-stepping (0.6) back up on the far side. The trajectory
     * layer finding this is the point -- the coarse layer only ever proposed a jump.
     */
    @Test
    fun `a one-block hole is sprinted across without a jump`() {
        val environment = gapEnvironment(holeAt = 3..3)
        val route = route(environment, Stance(6, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `a two-block hole is crossed by a discovered launch`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))
        assertTrue(
            route.edges.any { it.kind == CoarseMoveKind.JUMP_CANDIDATE },
            "the coarse layer must propose the jump",
        )

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump }, "a two-wide hole cannot be walked")
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 7.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `the launch beam retains distinct gait families instead of brake duplicates`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapSeeds = 6),
            ),
        )

        val oneLaunchFamilies = result.attempts
            .filter { it.parameters.gapLaunchFrames.size == 1 }
            .map { it.parameters.sprint to it.parameters.lookAheadNodes }
            .toSet()
        assertEquals(
            setOf(true to 1, true to 2, true to 3, false to 1, false to 2, false to 3),
            oneLaunchFamilies,
        )
    }

    @Test
    fun `a three-block hole is crossed by a discovered launch`() {
        val environment = gapEnvironment(holeAt = 3..5)
        val route = route(environment, Stance(8, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump })
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 8.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    /**
     * The same two-wide hole, floored with lava instead of void.
     *
     * A hole with lava in it is still a hole, but the search used to go blind at it. The
     * snapshot throws on the lava *during* the step, so the frame is never recorded and
     * `evaluate` never reaches its below-route test: the hazard arrives as
     * `UnsupportedPhysics`, which seeded nothing, and the whole search died at depth zero
     * having never pressed jump once. In the field this read as "84 attempts, 84
     * UnsupportedPhysics, search depths 0j[84]" -- a refusal with no search behind it.
     */
    @Test
    fun `a hole floored with lava still seeds launch discovery`() {
        val environment = lavaGapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(9, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
            "walking into lava is a fall like any other; a launch has to be discovered for it",
        )

        assertTrue(result.tape.asList().any { it.jump }, "a two-wide hole cannot be walked")
        assertTrue(result.parameters.gapLaunchFrames.isNotEmpty())
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(
            result.rollout.frames.none { frame -> frame.state.position.y < -0.5 },
            "the certified tape must never dip into the lava it is clearing",
        )
    }

    @Test
    fun `two gaps are crossed by one recursively discovered launch schedule`() {
        val environment = gapEnvironment(3..4, 9..10)
        val route = route(environment, Stance(13, 0, 0))
        assertTrue(
            route.edges.count { it.kind == CoarseMoveKind.JUMP_CANDIDATE } >= 2,
            "the coarse route must contain both crossings: ${route.edges}",
        )

        val search = WalkingSeedSearch.search(route, initialState(), PROFILE, environment)
        val failed = search as? WalkingSeedSearchResult.NoSafeStop
        val result = assertIs<WalkingSeedSearchResult.Success>(
            search,
            "multi-gap search failed; attempts by launches=" + failed?.attempts
                ?.groupingBy { it.parameters.gapLaunchFrames.size to it.diagnostic?.javaClass?.simpleName }
                ?.eachCount(),
        )

        assertEquals(2, result.parameters.gapLaunchFrames.size)
        assertTrue(result.parameters.gapLaunchFrames.zipWithNext().all { (a, b) -> a < b })
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 13.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `three descending gaps are crossed by one launch schedule`() {
        val environment = descendingGapChainEnvironment()
        val route = route(environment, Stance(17, -3, 0))
        assertTrue(
            route.edges.count {
                it.kind == CoarseMoveKind.JUMP_CANDIDATE && it.to.y - it.from.y == -1
            } >= 3,
            "the coarse route must contain all descending crossings: ${route.edges}",
        )

        val search = WalkingSeedSearch.search(route, initialState(), PROFILE, environment)
        val failed = search as? WalkingSeedSearchResult.NoSafeStop
        val result = assertIs<WalkingSeedSearchResult.Success>(
            search,
            "descending chain failed; attempts by launches=" + failed?.attempts
                ?.groupingBy { it.parameters.gapLaunchFrames.size to it.diagnostic?.javaClass?.simpleName }
                ?.eachCount() + "; nearest=" + failed?.nearest,
        )

        assertTrue(result.parameters.gapLaunchFrames.size >= 3)
        assertEquals(-3.0, result.rollout.finalState.position.y, 0.05)
        assertTrue(hypot(result.rollout.finalState.position.x - 17.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `a moving suffix keeps momentum through three descending gaps`() {
        val environment = descendingGapChainEnvironment()
        val fullRoute = route(environment, Stance(17, -3, 0))
        val spliceIndex = fullRoute.nodes.indexOf(Stance(2, 0, 0))
        assertTrue(spliceIndex > 0)

        val simulator = MovementSimulator(PROFILE, environment, initialState())
        repeat(9) {
            simulator.tickMovement(MovementSimulationInput(forward = 1.0, sprint = true))
        }
        val moving = simulator.state
        assertTrue(moving.onGround)

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(fullRoute.suffix(spliceIndex), moving, PROFILE, environment),
        )

        assertTrue(
            result.parameters.gapLaunchFrames.firstOrNull() in 0..3,
            "moving suffix waited too long to launch: ${result.parameters.gapLaunchFrames}",
        )
        assertTrue(result.parameters.gapLaunchFrames.size >= 3)
        assertEquals(-3.0, result.rollout.finalState.position.y, 0.05)
    }

    @Test
    fun `one launch depth cannot certify a route containing two gaps`() {
        val environment = gapEnvironment(3..4, 9..10)
        val route = route(environment, Stance(13, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapJumps = 1),
            ),
        )

        assertTrue(result.attempts.any { it.parameters.gapLaunchFrames.size == 1 })
        assertTrue(result.attempts.none { it.parameters.gapLaunchFrames.size > 1 })
    }

    @Test
    fun `a two-stance crossing may land one block down`() {
        val environment = descendingGapEnvironment()
        val route = route(environment, Stance(6, -1, 0), maxJumpSpan = 2)

        assertTrue(
            route.edges.any {
                it.kind == CoarseMoveKind.JUMP_CANDIDATE &&
                    it.to.x - it.from.x == 2 && it.to.y - it.from.y == -1
            },
            "the coarse layer must retain the span-two descending connection",
        )

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
        assertEquals(-1.0, result.rollout.finalState.position.y, 0.05)
    }

    @Test
    fun `a two-block hole may land one block down`() {
        val environment = offsetLandingGapEnvironment(holeAt = 3..4, landingOffset = -1)
        val route = route(environment, Stance(7, -1, 0))

        assertTrue(route.edges.any {
            it.kind == CoarseMoveKind.JUMP_CANDIDATE && it.to.y - it.from.y == -1
        })
        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump })
        assertEquals(-1.0, result.rollout.finalState.position.y, 0.05)
    }

    @Test
    fun `a one-block hole may land one block up`() {
        val environment = offsetLandingGapEnvironment(holeAt = 3..3, landingOffset = 1)
        val route = route(environment, Stance(6, 1, 0))

        assertTrue(route.edges.any {
            it.kind == CoarseMoveKind.JUMP_CANDIDATE && it.to.y - it.from.y == 1
        })
        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump })
        assertEquals(1.0, result.rollout.finalState.position.y, 0.05)
    }

    @Test
    fun `a moving suffix can launch immediately for a rising gap`() {
        val environment = offsetLandingGapEnvironment(holeAt = 3..3, landingOffset = 1)
        val fullRoute = route(environment, Stance(6, 1, 0))
        val spliceIndex = fullRoute.nodes.indexOf(Stance(2, 0, 0))
        assertTrue(spliceIndex > 0, "route must approach the gap through stance x=2")

        // Emulate a continuously expanded controller handing over close to the
        // gap with sprint momentum. The rising candidate is now the suffix's first
        // edge, so frame zero is a legitimate (and often the only) launch choice.
        val simulator = MovementSimulator(PROFILE, environment, initialState())
        repeat(9) {
            simulator.tickMovement(MovementSimulationInput(forward = 1.0, sprint = true))
        }
        val moving = simulator.state
        assertTrue(moving.onGround)
        assertTrue(moving.velocity.horizontalLength() > WalkingSeedSearchConfig().stoppedSpeed)

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(fullRoute.suffix(spliceIndex), moving, PROFILE, environment),
        )

        assertTrue(
            result.parameters.gapLaunchFrames.firstOrNull() in 0..2,
            "expected a near-immediate moving-suffix launch, got ${result.parameters.gapLaunchFrames}; " +
                "splice position=${moving.position}, velocity=${moving.velocity}",
        )
        assertEquals(1.0, result.rollout.finalState.position.y, 0.05)
    }

    /** The launch has to be a *discovery*: the nominal walk must genuinely fall in. */
    @Test
    fun `without a discovered launch the nominal walk falls into the hole`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))

        // No launch lattice to search: the walk can only fall.
        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapSeeds = 0),
            ),
        )

        assertTrue(
            result.attempts.mapNotNull { it.diagnostic }.any { it is TrajectoryDiagnostic.FellBelowRoute },
            "the nominal walk must fall in -- that failure is what drives the search",
        )
    }

    @Test
    fun `a hazard-driven continuation keeps moving runway before the gap`() {
        val environment = gapEnvironment(holeAt = 8..9)
        val route = route(environment, Stance(13, 0, 0))

        val refused = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapSeeds = 0),
            ),
        )
        val extension = checkNotNull(refused.extension)

        assertTrue(extension.sourceDiagnostic is TrajectoryDiagnostic.FellBelowRoute)
        assertTrue(
            extension.sourceDiagnostic.frame - extension.tape.frameCount >= 12,
            "hazard splice left too little runway: diagnostic=${extension.sourceDiagnostic.frame}, " +
                "prefix=${extension.tape.frameCount}",
        )
        assertTrue(extension.rollout.finalState.velocity.horizontalLength() > WalkingSeedSearchConfig().stoppedSpeed)
    }

    @Test
    fun `continuous expansion preserves runway after an ascent before a descending gap`() {
        val environment = ascentThenDescendingGapEnvironment()
        val route = route(
            environment = environment,
            goal = Stance(28, 2, 0),
            allowStepUp = true,
        )
        assertTrue(
            route.edges.count { it.to.y > it.from.y } >= 3,
            "route must climb three levels before the gap: ${route.edges}",
        )
        assertTrue(route.edges.any {
            it.kind == CoarseMoveKind.JUMP_CANDIDATE &&
                it.to.y - it.from.y == -1 && it.to.x - it.from.x == 4
        }, "route must contain the long descending crossing: ${route.edges}")

        val search = WalkingSeedSearch.searchContinuously(
            route, initialState(), PROFILE, environment,
            WalkingSeedSearchConfig(maxGapJumps = 3),
        )
        val failed = search as? WalkingSeedSearchResult.NoSafeStop
        val result = assertIs<WalkingSeedSearchResult.Success>(
            search,
            "segments=${failed?.completedSegments}, splices=${failed?.spliceFrames}, " +
                "remaining=${failed?.remainingStart}->${failed?.remainingGoal}, " +
                "depths=${failed?.attempts?.groupingBy { it.parameters.gapLaunchFrames.size to it.diagnostic?.javaClass?.simpleName }?.eachCount()}",
        )

        assertTrue(result.controlSegments > 1)
        assertTrue(result.spliceFrames.isNotEmpty())
        assertTrue(result.parameters.gapLaunchFrames.isNotEmpty())
        assertEquals(2.0, result.rollout.finalState.position.y, 0.05)
        assertTrue(
            hypot(result.rollout.finalState.position.x - 28.5, result.rollout.finalState.position.z - 0.5) <= 0.20,
        )
    }

    /**
     * Beyond the mask's reach the graph simply has no edge, so the goal is
     * unreachable rather than optimistically attempted.
     */
    @Test
    fun `a hole wider than the jump mask leaves no coarse route at all`() {
        val environment = gapEnvironment(holeAt = 3..9)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = true,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(12, 0, 0))
        planner.repair(Duration.INFINITE)

        assertNull(planner.routePlan(snapshotRevision = 31L), "a 7-wide hole has no candidate edge")
    }

    /** The published tape must reproduce exactly: it is the executable plan. */
    @Test
    fun `the discovered jump tape replays deterministically`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))
        val initial = initialState()

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initial, PROFILE, environment),
        )

        val replay = MovementSimulator(PROFILE, environment, initial)
        result.tape.asList().forEach { replay.tickMovement(it) }
        assertEquals(result.rollout.finalState, replay.state)
    }

    private fun route(
        environment: SnapshotSimulationEnvironment,
        goal: Stance,
        maxJumpSpan: Int = 4,
        allowStepUp: Boolean = false,
    ) = run {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = allowStepUp,
                maxWalkOffDepth = 0,
                allowJumpCandidates = true,
                maxJumpSpan = maxJumpSpan,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        requireNotNull(planner.routePlan(snapshotRevision = 30L))
    }

    /** Flat floor along +X with the floor missing under [holeAt], and a void below. */
    private fun gapEnvironment(holeAt: IntRange, vararg additionalHoles: IntRange): SnapshotSimulationEnvironment {
        val holes = listOf(holeAt) + additionalHoles
        val blocks = buildMap {
            for (x in -3..14) for (z in -3..3) {
                if (holes.any { x in it }) continue
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 14, 5, 3),
            blocks = blocks,
        )
    }

    /** The same flat floor with a hole, but the hole is floored with lava rather than void. */
    private fun lavaGapEnvironment(holeAt: IntRange): SnapshotSimulationEnvironment {
        val lava = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID, "minecraft:lava"),
        )
        val blocks = buildMap {
            for (x in -3..14) for (z in -3..3) {
                if (x in holeAt) {
                    for (y in -3..-1) put(BlockPos(x, y, z), lava)
                } else {
                    put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
                }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 14, 5, 3),
            blocks = blocks,
        )
    }

    /** One missing support cell, followed by a floor one block lower. */
    private fun descendingGapEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..14) for (z in -3..3) {
                when {
                    x <= 2 -> put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
                    x >= 4 -> put(BlockPos(x, -2, z), SnapshotBlockPhysics.FULL_CUBE)
                }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 14, 5, 3),
            blocks = blocks,
        )
    }

    /** Three two-wide crossings, each landing one block below the prior platform. */
    private fun descendingGapChainEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..20) for (z in -3..3) {
                val supportY = when (x) {
                    in -3..2 -> -1
                    in 5..7 -> -2
                    in 10..12 -> -3
                    in 15..20 -> -4
                    else -> null
                }
                supportY?.let { put(BlockPos(x, it, z), SnapshotBlockPhysics.FULL_CUBE) }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 20, 5, 3),
            blocks = blocks,
        )
    }

    /** Three spaced rises feed directly into a span-four landing one block down. */
    private fun ascentThenDescendingGapEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..32) for (z in -3..3) {
                val supportY = when (x) {
                    in -3..2 -> -1
                    in 3..5 -> 0
                    in 6..8 -> 1
                    in 9..12 -> 2
                    in 16..32 -> 1
                    else -> null
                }
                supportY?.let { put(BlockPos(x, it, z), SnapshotBlockPhysics.FULL_CUBE) }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 32, 8, 3),
            blocks = blocks,
        )
    }

    /** Flat approach, a gap, then a platform offset vertically from the approach. */
    private fun offsetLandingGapEnvironment(
        holeAt: IntRange,
        landingOffset: Int,
    ): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..14) for (z in -3..3) {
                when {
                    x < holeAt.first -> put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
                    x > holeAt.last -> put(BlockPos(x, landingOffset - 1, z), SnapshotBlockPhysics.FULL_CUBE)
                }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 14, 6, 3),
            blocks = blocks,
        )
    }

    private fun initialState() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 0.0, 0.5),
        rotation = Rotation(-90.0, 0.0),
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
