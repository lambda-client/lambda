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
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.pathing.trajectory.tailLowerBound
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Phase 0 admissibility guard: `elapsed + tailLowerBound(state)` is a lower bound on
 * the total travel time of the tape the state belongs to, so along a certified
 * rollout's own frames it must never exceed the certified total. If it did, the
 * ranking would punish genuinely faster continuations -- the exact disease the
 * total-time objective replaced.
 */
class TailAdmissibilityTest {
    @Test
    fun `elapsed plus tail never exceeds the certified total along its own rollout`() {
        for ((name, fixture) in FIXTURES) {
            val (environment, start, goal) = fixture()
            val moves = SimpleMoveLibrary.build(
                costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
                options = SimpleMoveOptions(allowDiagonal = false, maxWalkOffDepth = 2),
            )
            val planner = CoarsePlanner(environment, moves, start, goal)
            assertTrue(planner.repair(Duration.INFINITE).converged, "$name: no coarse route")
            val route = requireNotNull(planner.routePlan(snapshotRevision = 1L))

            val result = WalkingSeedSearch.search(route, initialState(), PROFILE, environment)
            val success = assertIs<WalkingSeedSearchResult.Success>(result, "$name refused")

            val total = success.tape.frameCount.toDouble()
            val nodes = route.nodes
            var progress = 0
            success.rollout.frames.forEachIndexed { index, frame ->
                // Same monotone bounded-advance projection the search itself uses.
                val limit = minOf(nodes.lastIndex, progress + 2)
                var best = progress
                var bestDistance = distance(frame.state, nodes[progress])
                for (candidate in progress + 1..limit) {
                    val d = distance(frame.state, nodes[candidate])
                    if (d < bestDistance) {
                        best = candidate
                        bestDistance = d
                    }
                }
                progress = best

                val tail = tailLowerBound(frame.state, route, progress)
                assertTrue(
                    index + 1 + tail.ticks <= total + TOLERANCE_TICKS,
                    "$name frame $index: elapsed ${index + 1} + tail ${"%.2f".format(tail.ticks)} " +
                        "exceeds the certified total $total",
                )
            }
        }
    }

    private fun distance(state: MovementSimulationState, node: Stance): Double {
        val dx = state.position.x - (node.x + 0.5)
        val dy = state.position.y - node.y
        val dz = state.position.z - (node.z + 0.5)
        return dx * dx + dy * dy + dz * dz
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

        const val TOLERANCE_TICKS = 1e-6

        data class Fixture(
            val environment: SnapshotSimulationEnvironment,
            val start: Stance,
            val goal: Stance,
        )

        val FIXTURES: Map<String, () -> Fixture> = mapOf(
            "flat-run" to {
                Fixture(
                    ground(xs = -3..12, zs = -3..3, floorY = -1),
                    Stance(0, 0, 0),
                    Stance(10, 0, 0),
                )
            },
            "staircase" to {
                val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
                for (x in -3..12) for (z in -3..3) blocks[BlockPos(x, -1, z)] = SnapshotBlockPhysics.FULL_CUBE
                for (step in 1..3) {
                    for (x in 2 * step..12) for (z in -3..3) {
                        blocks[BlockPos(x, step - 1, z)] = SnapshotBlockPhysics.FULL_CUBE
                    }
                }
                Fixture(
                    SnapshotSimulationEnvironment.synthetic(
                        SimulationSnapshotBounds(-4, -3, -4, 14, 9, 4),
                        blocks,
                    ),
                    Stance(0, 0, 0),
                    Stance(9, 3, 0),
                )
            },
            "gap-jump" to {
                val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
                for (x in -3..2) for (z in -3..3) blocks[BlockPos(x, -1, z)] = SnapshotBlockPhysics.FULL_CUBE
                for (x in 5..10) for (z in -3..3) blocks[BlockPos(x, -1, z)] = SnapshotBlockPhysics.FULL_CUBE
                Fixture(
                    SnapshotSimulationEnvironment.synthetic(
                        SimulationSnapshotBounds(-4, -8, -4, 12, 8, 4),
                        blocks,
                    ),
                    Stance(0, 0, 0),
                    Stance(8, 0, 0),
                )
            },
        )

        fun ground(xs: IntRange, zs: IntRange, floorY: Int): SnapshotSimulationEnvironment {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            for (x in xs) for (z in zs) blocks[BlockPos(x, floorY, z)] = SnapshotBlockPhysics.FULL_CUBE
            return SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(xs.first - 1, floorY - 2, zs.first - 1, xs.last + 2, floorY + 8, zs.last + 1),
                blocks,
            )
        }
    }
}
