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
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.math.hypot

data class WalkingSeedSearchConfig(
    val maxFrames: Int = 160,
    val maxYawDegreesPerFrame: Double = 30.0,
    val goalRadius: Double = 0.20,
    val stoppedSpeed: Double = 0.012,
    val stableStopFrames: Int = 3,
    val maxCorridorDeviation: Double = 1.5,
    val lookAheadNodes: List<Int> = listOf(1, 2, 3),
    val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
    val stepUpJumpLeadDistances: List<Double> = listOf(0.30, 0.55, 0.80, 1.05),
    val sprintModes: List<Boolean> = listOf(true, false),
) {
    init {
        require(maxFrames > 0)
        require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
        require(goalRadius > 0.0 && goalRadius.isFinite())
        require(stoppedSpeed >= 0.0 && stoppedSpeed.isFinite())
        require(stableStopFrames > 0)
        require(maxCorridorDeviation > 0.0 && maxCorridorDeviation.isFinite())
        require(lookAheadNodes.isNotEmpty() && lookAheadNodes.all { it > 0 })
        require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
        require(stepUpJumpLeadDistances.isNotEmpty() && stepUpJumpLeadDistances.all { it > 0.0 && it.isFinite() })
        require(sprintModes.isNotEmpty())
    }
}

data class WalkingSeedParameters(
    val sprint: Boolean,
    val lookAheadNodes: Int,
    val brakeDistance: Double,
    val stepUpJumpLeadDistance: Double?,
)

data class WalkingSeedAttempt(
    val parameters: WalkingSeedParameters,
    val simulatedFrames: Int,
    val finalGoalError: Double,
    val finalHorizontalSpeed: Double,
    val rejectedByEnvironment: Boolean,
    val collidedHorizontally: Boolean,
    val leftCorridor: Boolean,
)

sealed interface WalkingSeedSearchResult {
    data class Success(
        val sourceRoute: CoarseRoutePlan,
        val tape: InputTape,
        val rollout: TrajectoryRollout,
        val parameters: WalkingSeedParameters,
        /** Union of coarse template reads and every snapshot voxel read by simulation. */
        val dependencies: Set<VoxelPos>,
        val attempts: List<WalkingSeedAttempt>,
    ) : WalkingSeedSearchResult

    data class UnsupportedRoute(val edgeKinds: Set<CoarseMoveKind>) : WalkingSeedSearchResult

    data class NoSafeStop(val attempts: List<WalkingSeedAttempt>) : WalkingSeedSearchResult
}

/**
 * First M3 search slice: seed a few smooth corridor followers, simulate each,
 * and publish only a stopped, collision-free input tape. A typed [CoarseMoveKind.STEP_UP]
 * may press jump near its declared takeoff; gaps, drops, and candidate jumps
 * remain explicitly unsupported.
 */
object WalkingSeedSearch {
    fun search(
        route: CoarseRoutePlan,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
    ): WalkingSeedSearchResult {
        val supportedKinds = setOf(CoarseMoveKind.WALK, CoarseMoveKind.STEP_UP)
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }.filterTo(HashSet()) { it !in supportedKinds }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        val nodes = route.nodes.map { it.center() }
        val goal = nodes.last()
        val attempts = ArrayList<WalkingSeedAttempt>()
        var best: WalkingSeedSearchResult.Success? = null
        val jumpLeadDistances: List<Double?> = if (CoarseMoveKind.STEP_UP in route.edges.map { it.kind }) {
            config.stepUpJumpLeadDistances
        } else {
            listOf(null)
        }

        for (sprint in config.sprintModes) {
            for (lookAhead in config.lookAheadNodes) {
                for (brakeDistance in config.brakeDistances) {
                    for (jumpLeadDistance in jumpLeadDistances) {
                        val parameters = WalkingSeedParameters(sprint, lookAhead, brakeDistance, jumpLeadDistance)
                        val program = CorridorWalkingProgram(route.nodes, parameters, config.maxYawDegreesPerFrame)
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = initialState,
                            profile = profile,
                            environment = environment,
                            program = program,
                            frameCount = config.maxFrames,
                        )
                        val collision = rollout.frames.any { it.state.horizontalCollision }
                        val leftCorridor = rollout.frames.any {
                            horizontalDistanceToPolyline(it.state.position.x, it.state.position.z, nodes) > config.maxCorridorDeviation
                        }
                        val stopFrame = if (!collision && !leftCorridor) stableStopFrame(rollout, goal, config) else null
                        val final = rollout.finalState
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            finalHorizontalSpeed = final.velocity.horizontalLength(),
                            rejectedByEnvironment = !rollout.completed,
                            collidedHorizontally = collision,
                            leftCorridor = leftCorridor,
                        )

                        if (stopFrame != null) {
                            val tape = InputTape(rollout.frames.take(stopFrame + 1).map { it.input })
                            // Replay only the published prefix on a fresh tracker:
                            // candidate probing after the stop must not inflate
                            // the plan's correctness-bearing dependency set.
                            val exactEnvironment = environment.trackingView()
                            val certified = TrajectoryRolloutEngine.rollout(
                                initialState = initialState,
                                profile = profile,
                                environment = exactEnvironment,
                                program = tape,
                                frameCount = tape.frameCount,
                            )
                            check(certified.completed && certified.frames.size == tape.frameCount)
                            val success = WalkingSeedSearchResult.Success(
                                sourceRoute = route,
                                tape = tape,
                                rollout = certified,
                                parameters = parameters,
                                dependencies = route.dependencies + exactEnvironment.dependencies(),
                                attempts = emptyList(),
                            )
                            if (best == null || success.tape.frameCount < best.tape.frameCount) best = success
                        }
                    }
                }
            }
        }

        return best?.copy(attempts = attempts.toList()) ?: WalkingSeedSearchResult.NoSafeStop(attempts.toList())
    }

    private fun stableStopFrame(
        rollout: TrajectoryRollout,
        goal: HorizontalPoint,
        config: WalkingSeedSearchConfig,
    ): Int? {
        var stable = 0
        for (frame in rollout.frames) {
            val state = frame.state
            val atGoal = hypot(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
                kotlin.math.abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
            val stopped = state.velocity.horizontalLength() <= config.stoppedSpeed
            stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
            if (stable >= config.stableStopFrames) return frame.index
        }
        return null
    }

    private class CorridorWalkingProgram(
        stanceNodes: List<Stance>,
        private val parameters: WalkingSeedParameters,
        private val maxYawChange: Double,
    ) : ControlProgram {
        private val nodes = stanceNodes.map { it.center() }
        private val rises = stanceNodes.zipWithNext().mapIndexedNotNull { index, (from, to) ->
            index.takeIf { to.y > from.y }
        }

        /** Polyline distance from each node to the goal; the tail of the route. */
        private val distanceToGoal = DoubleArray(nodes.size).also { suffix ->
            for (index in nodes.lastIndex - 1 downTo 0) {
                suffix[index] = suffix[index + 1] + horizontalDistance(nodes[index], nodes[index + 1])
            }
        }

        /**
         * Pure-pursuit progress. It only ever advances, and by at most
         * [MAX_PROGRESS_ADVANCE] nodes per tick: a route that doubles back passes
         * close to its own earlier nodes, and a global nearest-node search would
         * snap the target across the fold and steer straight through the obstacle.
         */
        private var progressIndex = 0
        private var braking = false
        private var nextRise = 0
        private var jumpWasAirborne = false

        override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
            advanceProgress(observed)

            // Resolves this tick's launch and retires a rise once its landing is
            // observed, so it must run before the brake decision consumes it.
            val jump = shouldJump(observed)

            // Brake on distance remaining *along the route*, not straight-line
            // distance to the goal: around an obstacle the player can be a stride
            // from the goal as the crow flies while most of the path is still
            // ahead, and braking there stalls short of the corner. A pending rise
            // also holds the brake off -- a coasting player has no momentum to
            // clear a step-up, so a rise on the final edge could never launch.
            val risePending = nextRise < rises.size
            if (!risePending && remainingPathDistance(observed) <= parameters.brakeDistance) braking = true
            if (braking) {
                return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
            }

            val target = nodes[minOf(nodes.lastIndex, progressIndex + parameters.lookAheadNodes)]
            val desiredYaw = Math.toDegrees(atan2(target.z - observed.position.z, target.x - observed.position.x)) - 90.0
            val yawDelta = Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
            return MovementSimulationInput(
                forward = 1.0,
                sprint = parameters.sprint,
                jump = jump,
                rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
            )
        }

        private fun advanceProgress(observed: MovementSimulationState) {
            val limit = minOf(nodes.lastIndex, progressIndex + MAX_PROGRESS_ADVANCE)
            var best = progressIndex
            var bestSquared = horizontalDistanceSquared(nodes[progressIndex], observed)
            for (index in progressIndex + 1..limit) {
                val squared = horizontalDistanceSquared(nodes[index], observed)
                if (squared < bestSquared) {
                    bestSquared = squared
                    best = index
                }
            }
            progressIndex = best
        }

        private fun remainingPathDistance(observed: MovementSimulationState): Double {
            val next = minOf(nodes.lastIndex, progressIndex + 1)
            val toNext = hypot(nodes[next].x - observed.position.x, nodes[next].z - observed.position.z)
            return toNext + distanceToGoal[next]
        }

        private fun shouldJump(observed: MovementSimulationState): Boolean {
            val leadDistance = parameters.stepUpJumpLeadDistance ?: return false
            if (nextRise >= rises.size) return false
            if (!observed.onGround) {
                jumpWasAirborne = true
                return false
            }
            if (jumpWasAirborne) {
                nextRise++
                jumpWasAirborne = false
                if (nextRise >= rises.size) return false
            }
            val takeoff = nodes[rises[nextRise]]
            return hypot(takeoff.x - observed.position.x, takeoff.z - observed.position.z) <= leadDistance
        }

        private fun horizontalDistanceSquared(node: HorizontalPoint, observed: MovementSimulationState): Double {
            val dx = node.x - observed.position.x
            val dz = node.z - observed.position.z
            return dx * dx + dz * dz
        }

        private companion object {
            /** A tick advances well under one block, so two nodes is generous headroom. */
            const val MAX_PROGRESS_ADVANCE = 2
        }
    }

    private data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

    private fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

    private fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
        hypot(to.x - from.x, to.z - from.z)

    private fun horizontalDistanceToPolyline(x: Double, z: Double, nodes: List<HorizontalPoint>): Double {
        if (nodes.size == 1) return hypot(x - nodes[0].x, z - nodes[0].z)
        var bestSquared = Double.POSITIVE_INFINITY
        for (index in 0 until nodes.lastIndex) {
            val a = nodes[index]
            val b = nodes[index + 1]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val lengthSquared = dx * dx + dz * dz
            val projection = if (lengthSquared == 0.0) 0.0 else
                (((x - a.x) * dx + (z - a.z) * dz) / lengthSquared).coerceIn(0.0, 1.0)
            val ex = x - (a.x + projection * dx)
            val ez = z - (a.z + projection * dz)
            bestSquared = minOf(bestSquared, ex * ex + ez * ez)
        }
        return kotlin.math.sqrt(bestSquared)
    }

    private const val VERTICAL_GOAL_TOLERANCE = 0.05
}
