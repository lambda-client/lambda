/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.neural

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.trajectory.InputTape
import com.lambda.pathing.trajectory.SimulatedTrajectoryFrame
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryRollout
import com.lambda.pathing.trajectory.TrajectoryRolloutTermination
import com.lambda.pathing.trajectory.WalkingSeedAttempt
import com.lambda.pathing.trajectory.WalkingSeedParameters
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.pathing.trajectory.tailLowerBound
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Discovers a trajectory by rolling the trained value-guided policy out through the
 * exact simulator, producing the same [WalkingSeedSearchResult] the seed search does.
 *
 * The policy proposes; the simulator remains the authority. Every frame is exactly
 * simulated, so a rollout that reaches a stable grounded stop at the goal *is* a
 * certified tape — the identical publication the seed search yields, replayed by the
 * unchanged executor. A rollout that falls, leaves the snapshot, or fails to stop is a
 * [WalkingSeedSearchResult.NoSafeStop] scoped to this entry state only (a neural miss
 * never proves a coarse edge impossible, so it must never blacklist D*).
 *
 * The observation is built from the live simulated state exactly as the training
 * environment builds it (dense physics + tail-cost value features + a local occupancy
 * window); any drift here silently shifts the policy's input distribution.
 */
object NeuralTrajectoryDiscovery {
    fun search(
        route: CoarseRoutePlan,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        policy: NeuralMovementPolicy,
        maxFrames: Int,
        goalRadius: Double,
    ): WalkingSeedSearchResult {
        val (points, coarseIndices) = densify(route)
        val target = points.last()
        val goalNode = route.nodes.last()
        val random = java.util.Random(route.snapshotRevision xor route.routeVersion xor 0x9E3779B97F4A7C15uL.toLong())

        val startValue = valueAt(initialState, route, 0).coerceAtLeast(1.0)
        // The tape spans the whole route, not the seed search's short horizon, so the
        // budget scales with admissible travel time. A rolling local goal (see [observe])
        // keeps the goal feature in the trained range regardless of total length.
        val budget = Math.ceil(startValue * FRAME_BUDGET_FACTOR).toInt().coerceIn(maxFrames, MAX_FRAME_BUDGET)
        val frames = ArrayList<SimulatedTrajectoryFrame>(budget)
        val inputs = ArrayList<MovementSimulationInput>(budget)

        // Track the voxels the simulation actually reads so the published tape carries
        // real dependencies (Phase 5 invalidation, and the gametest's dependency check),
        // exactly as the seed search does. Every restored simulator shares this tracker.
        val tracked = snapshot.trackingView()
        var simulator = MovementSimulator(profile = profile, environment = tracked, initialState = initialState)
        var routeCursor = 0
        var previousValue = startValue
        var bestCursor = 0
        var noProgress = 0
        var escape = 0
        var retries = MAX_RESTORES

        // Checkpoint of the furthest grounded progress: the greedy line is restored here
        // and resamples stochastically to clear the obstacle it stalled on. The start is
        // the first checkpoint so an early from-rest stall can also retry with sampling.
        var cpFrame = 0
        var cpState = initialState
        var cpCursor = 0
        var cpValue = startValue

        fun refusal(diagnostic: TrajectoryDiagnostic) = WalkingSeedSearchResult.NoSafeStop(
            attempts = listOf(
                WalkingSeedAttempt(
                    parameters = NEURAL_PARAMETERS,
                    simulatedFrames = frames.size,
                    finalGoalError = distance(simulator.state.position, target),
                    finalHorizontalSpeed = hypot(simulator.state.velocity.x, simulator.state.velocity.z),
                    diagnostic = diagnostic,
                    blockedProgress = coarseCursor(bestCursor, coarseIndices, route),
                ),
            ),
            remainingStart = route.nodes.first(),
            remainingGoal = goalNode,
            remainingMoveSummary = "neural rollout did not certify a stop",
            blockedProgress = coarseCursor(bestCursor, coarseIndices, route),
            edgeFailureScope = WalkingSeedSearchResult.EdgeFailureScope.CURRENT_ENTRY,
        )

        fun restore(): Boolean {
            if (retries <= 0) return false
            retries--
            simulator = MovementSimulator(profile = profile, environment = tracked, initialState = cpState)
            if (frames.size > cpFrame) frames.subList(cpFrame, frames.size).clear()
            if (inputs.size > cpFrame) inputs.subList(cpFrame, inputs.size).clear()
            routeCursor = cpCursor
            previousValue = cpValue
            bestCursor = cpCursor
            noProgress = 0
            escape = ESCAPE_FRAMES
            return true
        }

        while (inputs.size < budget) {
            val frame = inputs.size
            val state = simulator.state
            val observation = observe(
                state, points, coarseIndices, route, target, routeCursor,
                frame, budget, startValue, previousValue, snapshot,
            )
            val action = if (escape > 0) {
                escape--
                policy.sample(observation, ESCAPE_TEMPERATURE, random)
            } else {
                policy.act(observation)
            }
            // Pressing jump in the air is a no-op the policy learned to spam; mask it so
            // the tape is clean. This cannot change the physics, so the certified rollout
            // is identical -- only the recorded input is tidier.
            if (!state.onGround) action[2] = 0
            val input = policy.inputFor(action, state.rotation.yaw)
            when (simulator.tryTickMovement(input)) {
                is MovementSimulationStepResult.Rejected -> {
                    if (restore()) continue
                    return refusal(TrajectoryDiagnostic.UnsupportedPhysics(frame, "neural rollout left the snapshot"))
                }
                is MovementSimulationStepResult.Advanced -> Unit
            }
            val after = simulator.state
            frames += SimulatedTrajectoryFrame(index = frame, input = input, state = after)
            inputs += input

            routeCursor = advanceCursor(after.position, points, routeCursor)
            previousValue = valueAt(after, route, coarseCursor(routeCursor, coarseIndices, route))

            val goalError = distance(after.position, target)
            val speed = hypot(after.velocity.x, after.velocity.z)
            if (after.onGround && goalError <= goalRadius && speed <= STOP_TOLERANCE) {
                return WalkingSeedSearchResult.Success(
                    sourceRoute = route,
                    tape = InputTape(inputs),
                    rollout = TrajectoryRollout(initialState, frames, TrajectoryRolloutTermination.Completed),
                    parameters = NEURAL_PARAMETERS,
                    dependencies = route.dependencies + tracked.dependencies(),
                    attempts = listOf(
                        WalkingSeedAttempt(NEURAL_PARAMETERS, frames.size, goalError, speed, null, route.nodes.lastIndex),
                    ),
                )
            }
            if (after.position.y < initialState.position.y - FALL_DROP) {
                if (restore()) continue
                return refusal(TrajectoryDiagnostic.HarmfulFall(frame, after.position.y))
            }
            if (routeCursor > bestCursor) {
                bestCursor = routeCursor
                noProgress = 0
                if (after.onGround) {
                    cpFrame = inputs.size
                    cpState = after
                    cpCursor = routeCursor
                    cpValue = previousValue
                }
            } else if (++noProgress >= STALL_FRAMES) {
                if (restore()) continue
                return refusal(TrajectoryDiagnostic.NoStop(frame, goalError, speed))
            }
        }
        return refusal(TrajectoryDiagnostic.NoStop(budget, distance(simulator.state.position, target), hypot(simulator.state.velocity.x, simulator.state.velocity.z)))
    }

    private fun observe(
        state: MovementSimulationState,
        points: List<Vec3d>,
        coarseIndices: IntArray,
        route: CoarseRoutePlan,
        target: Vec3d,
        routeCursor: Int,
        frame: Int,
        maxFrames: Int,
        startValue: Double,
        previousValue: Double,
        snapshot: SnapshotSimulationEnvironment,
    ): FloatArray {
        val position = state.position
        val result = FloatArray(NeuralMovementPolicy.OBSERVATION_SIZE)
        var i = 0
        // Rolling local goal: the policy trained on goals within one ~48-block patch, so
        // a distant final goal is out of distribution. Point the goal feature at a node
        // a bounded lookahead ahead; it collapses to the true goal near the end, where
        // the stop is actually certified.
        val localGoal = points[(routeCursor + LOCAL_GOAL_LOOKAHEAD).coerceAtMost(points.lastIndex)]
        result[i++] = ((localGoal.x - position.x) / GOAL_SCALE_X).toFloat()
        result[i++] = ((localGoal.y - position.y) / GOAL_SCALE_Y).toFloat()
        result[i++] = ((localGoal.z - position.z) / GOAL_SCALE_Z).toFloat()
        result[i++] = (state.velocity.x / VELOCITY_SCALE).toFloat()
        result[i++] = (state.velocity.y / VELOCITY_SCALE).toFloat()
        result[i++] = (state.velocity.z / VELOCITY_SCALE).toFloat()
        val yawRadians = state.rotation.yaw * PI / 180.0
        result[i++] = sin(yawRadians).toFloat()
        result[i++] = cos(yawRadians).toFloat()
        result[i++] = if (state.onGround) 1.0f else 0.0f
        result[i++] = if (state.isSprinting) 1.0f else 0.0f
        result[i++] = state.jumpingCooldown / 10.0f
        result[i++] = if (state.horizontalCollision) 1.0f else 0.0f
        result[i++] = if (state.verticalCollision) 1.0f else 0.0f
        result[i++] = (frame.toDouble() / maxFrames).toFloat()
        result[i++] = ((points.lastIndex - routeCursor).toDouble() / points.size).toFloat()
        result[i++] = 0.0f
        result[i++] = 0.0f
        result[i++] = 1.0f
        for (offset in ANCHOR_OFFSETS) {
            val anchor = points[(routeCursor + offset).coerceAtMost(points.lastIndex)]
            result[i++] = ((anchor.x - position.x) / ANCHOR_SCALE_XZ).toFloat()
            result[i++] = ((anchor.y - position.y) / ANCHOR_SCALE_Y).toFloat()
            result[i++] = ((anchor.z - position.z) / ANCHOR_SCALE_XZ).toFloat()
        }
        result[i++] = (previousValue / VALUE_NORM).toFloat()
        result[i++] = (route.tailCosts[coarseCursor(routeCursor, coarseIndices, route)].lowerBound / VALUE_NORM).toFloat()
        result[i++] = ((startValue - previousValue) / startValue).toFloat()

        val centerX = floor(position.x).toInt()
        val centerY = floor(position.y).toInt()
        val centerZ = floor(position.z).toInt()
        for (dy in VOXEL_MIN_DY..VOXEL_MAX_DY) {
            for (dx in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                for (dz in -VOXEL_HALF_XZ..VOXEL_HALF_XZ) {
                    result[i++] = occupancy(snapshot, centerX + dx, centerY + dy, centerZ + dz)
                }
            }
        }
        return result
    }

    /** Solid (1) vs passable/out-of-snapshot (0), matching the training FULL_CUBE window. */
    private fun occupancy(snapshot: SnapshotSimulationEnvironment, x: Int, y: Int, z: Int): Float {
        if (BlockPos(x, y, z) !in snapshot.bounds) return 0.0f
        return if (snapshot.voxel(x, y, z).fullyPassable) 0.0f else 1.0f
    }

    private fun valueAt(state: MovementSimulationState, route: CoarseRoutePlan, coarseCursor: Int): Double =
        tailLowerBound(state, route, coarseCursor).ticks

    private fun coarseCursor(routeCursor: Int, coarseIndices: IntArray, route: CoarseRoutePlan): Int {
        var node = 0
        for (index in coarseIndices.indices) {
            if (coarseIndices[index] <= routeCursor) node = index else break
        }
        return node.coerceIn(0, route.nodes.lastIndex)
    }

    private fun advanceCursor(position: Vec3d, points: List<Vec3d>, routeCursor: Int): Int {
        val searchEnd = (routeCursor + 8).coerceAtMost(points.lastIndex)
        var bestIndex = routeCursor
        var bestDistance = nodeDistanceSquared(position, points[routeCursor])
        for (index in routeCursor + 1..searchEnd) {
            val distance = nodeDistanceSquared(position, points[index])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun densify(route: CoarseRoutePlan): Pair<List<Vec3d>, IntArray> {
        val corners = route.nodes.map { Vec3d(it.x + 0.5, it.y.toDouble(), it.z + 0.5) }
        val points = ArrayList<Vec3d>(corners.size * 2)
        val coarseIndices = IntArray(corners.size)
        points += corners.first()
        coarseIndices[0] = 0
        for (index in 1 until corners.size) {
            val from = corners[index - 1]
            val to = corners[index]
            val steps = ceil(hypot(to.x - from.x, to.z - from.z).coerceAtLeast(abs(to.y - from.y))).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val t = step.toDouble() / steps
                points += Vec3d(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t)
            }
            coarseIndices[index] = points.lastIndex
        }
        return points to coarseIndices
    }

    private fun distance(position: Vec3d, target: Vec3d): Double =
        sqrt(
            (target.x - position.x) * (target.x - position.x) +
                (target.y - position.y) * (target.y - position.y) +
                (target.z - position.z) * (target.z - position.z),
        )

    private fun nodeDistanceSquared(position: Vec3d, point: Vec3d): Double {
        val dx = position.x - point.x
        val dy = position.y - point.y
        val dz = position.z - point.z
        return dx * dx + dy * dy * 2.0 + dz * dz
    }

    private const val GOAL_SCALE_X = 48.0
    private const val GOAL_SCALE_Y = 8.0
    private const val GOAL_SCALE_Z = 25.0
    private const val VELOCITY_SCALE = 0.6
    private const val ANCHOR_SCALE_XZ = 12.0
    private const val ANCHOR_SCALE_Y = 6.0
    private const val VALUE_NORM = 200.0
    private const val VOXEL_HALF_XZ = 7
    private const val VOXEL_MIN_DY = -3
    private const val VOXEL_MAX_DY = 5
    private const val STOP_TOLERANCE = 0.115
    private const val VALUE_EPSILON = 1.0e-3
    private const val STALL_FRAMES = 30
    private const val FALL_DROP = 6.0
    private const val LOCAL_GOAL_LOOKAHEAD = 34
    private const val FRAME_BUDGET_FACTOR = 2.5
    private const val MAX_FRAME_BUDGET = 2000
    private const val MAX_RESTORES = 80
    private const val ESCAPE_FRAMES = 24
    private const val ESCAPE_TEMPERATURE = 1.0
    private val ANCHOR_OFFSETS = intArrayOf(3, 7, 11)
    private val NEURAL_PARAMETERS = WalkingSeedParameters(
        sprint = true,
        lookAheadNodes = 3,
        brakeDistance = 0.0,
        stepUpJumpLeadDistance = null,
    )
}
