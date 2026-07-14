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
    /** Vanilla takes fall damage above three blocks. Safety is a hard gate. */
    val maxSafeFallDistance: Double = 3.0,
    val lookAheadNodes: List<Int> = listOf(1, 2, 3),
    val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
    val stepUpJumpLeadDistances: List<Double> = listOf(0.30, 0.55, 0.80, 1.05),
    val sprintModes: List<Boolean> = listOf(true, false),
    /**
     * How far back from a fall the grounded launch lattice is searched. A launch can
     * only help if it happens shortly before the body leaves the ground.
     */
    val gapLaunchWindowFrames: Int = 14,
    /** How many failed walks are backtracked into launch searches. Bounded work. */
    val maxGapSeeds: Int = 6,
) {
    init {
        require(maxFrames > 0)
        require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
        require(goalRadius > 0.0 && goalRadius.isFinite())
        require(stoppedSpeed >= 0.0 && stoppedSpeed.isFinite())
        require(stableStopFrames > 0)
        require(maxCorridorDeviation > 0.0 && maxCorridorDeviation.isFinite())
        require(maxSafeFallDistance >= 0.0 && maxSafeFallDistance.isFinite())
        require(lookAheadNodes.isNotEmpty() && lookAheadNodes.all { it > 0 })
        require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
        require(stepUpJumpLeadDistances.isNotEmpty() && stepUpJumpLeadDistances.all { it > 0.0 && it.isFinite() })
        require(sprintModes.isNotEmpty())
        require(gapLaunchWindowFrames > 0)
        require(maxGapSeeds >= 0)
    }
}

data class WalkingSeedParameters(
    val sprint: Boolean,
    val lookAheadNodes: Int,
    val brakeDistance: Double,
    val stepUpJumpLeadDistance: Double?,
    /**
     * The grounded frame on which to press jump for a gap, discovered by backtracking
     * over a failed walk's trace. Null means the nominal walk pressed no gap jump.
     */
    val gapLaunchFrame: Int? = null,
)

data class WalkingSeedAttempt(
    val parameters: WalkingSeedParameters,
    val simulatedFrames: Int,
    val finalGoalError: Double,
    val finalHorizontalSpeed: Double,
    /** Null when this attempt reached a stable grounded stop at the goal. */
    val diagnostic: TrajectoryDiagnostic?,
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

    data class NoSafeStop(val attempts: List<WalkingSeedAttempt>) : WalkingSeedSearchResult {
        /** The failure the search got closest to solving; the first thing M4 should attack. */
        val nearest: WalkingSeedAttempt? get() = attempts.minByOrNull { it.finalGoalError }
    }

    /**
     * The accepted tape did not reproduce on a fresh replay. This must be impossible
     * -- same snapshot, same profile, same inputs -- so it is a typed result rather
     * than a crash in a worker thread, per invariant 6.
     */
    data class UnstableReplay(val reason: String) : WalkingSeedSearchResult
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
        val supportedKinds = setOf(
            CoarseMoveKind.WALK,
            CoarseMoveKind.STEP_UP,
            CoarseMoveKind.WALK_OFF,
            CoarseMoveKind.JUMP_CANDIDATE,
        )
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }.filterTo(HashSet()) { it !in supportedKinds }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        val nodes = route.nodes.map { it.center() }
        val goal = nodes.last()
        val attempts = ArrayList<WalkingSeedAttempt>()
        val jumpLeadDistances: List<Double?> = if (CoarseMoveKind.STEP_UP in route.edges.map { it.kind }) {
            config.stepUpJumpLeadDistances
        } else {
            listOf(null)
        }

        var best: Candidate? = null
        val launchSeeds = ArrayList<LaunchSeed>()

        for (sprint in config.sprintModes) {
            for (lookAhead in config.lookAheadNodes) {
                for (brakeDistance in config.brakeDistances) {
                    for (jumpLeadDistance in jumpLeadDistances) {
                        val parameters = WalkingSeedParameters(sprint, lookAhead, brakeDistance, jumpLeadDistance)
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = initialState,
                            profile = profile,
                            environment = environment,
                            program = CorridorWalkingProgram(route.nodes, parameters, config.maxYawDegreesPerFrame),
                            frameCount = config.maxFrames,
                        )

                        val evaluation = evaluate(rollout, nodes, goal, config)
                        val final = rollout.finalState
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            finalHorizontalSpeed = final.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                        )

                        // Only seed from obstacles a launch could actually clear.
                        val blocked = when (val diagnostic = evaluation.diagnostic) {
                            is TrajectoryDiagnostic.FellBelowRoute -> diagnostic.frame
                            is TrajectoryDiagnostic.HorizontalCollision -> diagnostic.frame
                            else -> null
                        }
                        if (blocked != null && parameters.gapLaunchFrame == null) {
                            launchSeeds += LaunchSeed(
                                parameters = parameters,
                                rollout = rollout,
                                blockedFrame = blocked,
                                goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            )
                        }

                        val frame = evaluation.stopFrame ?: continue
                        if (best == null || frame < best.stopFrame) {
                            best = Candidate(parameters, rollout, frame)
                        }
                    }
                }
            }
        }

        // Failure-directed branching (§7.3): a nominal walk that fell short of the
        // route did not fail randomly -- it told us exactly where it left the ground.
        // Backtrack over its grounded trace and try pressing jump on the ticks that
        // could still have carried it. Only a full simulation certifies the result.
        if (best == null && launchSeeds.isNotEmpty()) {
            for (candidate in launchSeeds.sortedBy { it.goalError }.take(config.maxGapSeeds)) {
                for (launch in launchLattice(candidate.rollout, candidate.blockedFrame, config)) {
                    val parameters = candidate.parameters.copy(gapLaunchFrame = launch)
                    val rollout = TrajectoryRolloutEngine.rollout(
                        initialState = initialState,
                        profile = profile,
                        environment = environment,
                        program = CorridorWalkingProgram(route.nodes, parameters, config.maxYawDegreesPerFrame),
                        frameCount = config.maxFrames,
                    )
                    val evaluation = evaluate(rollout, nodes, goal, config)
                    val final = rollout.finalState
                    attempts += WalkingSeedAttempt(
                        parameters = parameters,
                        simulatedFrames = rollout.frames.size,
                        finalGoalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                        finalHorizontalSpeed = final.velocity.horizontalLength(),
                        diagnostic = evaluation.diagnostic,
                    )
                    val frame = evaluation.stopFrame ?: continue
                    if (best == null || frame < best.stopFrame) {
                        best = Candidate(parameters, rollout, frame)
                    }
                }
            }
        }

        val winner = best ?: return WalkingSeedSearchResult.NoSafeStop(attempts.toList())

        // Only the winner is replayed for dependencies. Probing after the stop, and
        // every rejected candidate, must not inflate the plan's correctness-bearing
        // read set -- so the tape is re-run alone on a fresh tracker.
        val tape = InputTape(winner.rollout.frames.take(winner.stopFrame + 1).map { it.input })
        val tracked = environment.trackingView()
        val certified = TrajectoryRolloutEngine.rollout(
            initialState = initialState,
            profile = profile,
            environment = tracked,
            program = tape,
            frameCount = tape.frameCount,
        )
        if (!certified.completed || certified.frames.size != tape.frameCount) {
            return WalkingSeedSearchResult.UnstableReplay(
                "accepted tape did not reproduce: ${certified.termination}"
            )
        }

        return WalkingSeedSearchResult.Success(
            sourceRoute = route,
            tape = tape,
            rollout = certified,
            parameters = winner.parameters,
            dependencies = route.dependencies + tracked.dependencies(),
            attempts = attempts.toList(),
        )
    }

    private class Candidate(
        val parameters: WalkingSeedParameters,
        val rollout: TrajectoryRollout,
        val stopFrame: Int,
    )

    /**
     * A walk that could not get past a point *on the ground*. The raw material of
     * jump discovery -- and the obstacle can be either kind:
     *
     * - it fell into a hole (`FellBelowRoute`), or
     * - it ran into a lip (`HorizontalCollision`).
     *
     * Both mean the same thing to a solver: the body needed to leave the ground here.
     * Seeding only from falls misses every rising jump, because a rise is a wall.
     */
    private class LaunchSeed(
        val parameters: WalkingSeedParameters,
        val rollout: TrajectoryRollout,
        val blockedFrame: Int,
        val goalError: Double,
    )

    /**
     * The grounded ticks shortly before the body left the route, latest first.
     *
     * Latest first because the last stance before the edge is the one with the most
     * run-up committed; earlier launches are the fallback when it overshoots.
     */
    private fun launchLattice(
        rollout: TrajectoryRollout,
        blockedFrame: Int,
        config: WalkingSeedSearchConfig,
    ): List<Int> {
        val earliest = maxOf(0, blockedFrame - config.gapLaunchWindowFrames)
        return (blockedFrame - 1 downTo earliest)
            .filter { rollout.frames.getOrNull(it)?.state?.onGround == true }
    }

    private class Evaluation(val stopFrame: Int?, val diagnostic: TrajectoryDiagnostic?)

    /**
     * Walks the rollout once and reports the **first** thing that went wrong, so the
     * diagnostic names the earliest cause rather than a downstream symptom.
     */
    private fun evaluate(
        rollout: TrajectoryRollout,
        nodes: List<HorizontalPoint>,
        goal: HorizontalPoint,
        config: WalkingSeedSearchConfig,
    ): Evaluation {
        val floor = nodes.minOf { it.y } - FALL_TOLERANCE
        var stable = 0

        // Vanilla's fall distance is the drop from the apex of the current airborne
        // arc, and it resets on every landing -- so a chain of short walk-offs is
        // safe while one long one is not.
        var apex = rollout.initialState.position.y

        // A head bonk is remembered but not fatal: an arc can graze a ceiling and
        // still land where it meant to. It is only *reported* if the walk never
        // reaches a stable stop.
        var blocker: TrajectoryDiagnostic? = null

        rollout.frames.forEach { frame ->
            val state = frame.state
            val index = frame.index

            if (state.onGround) {
                val fallDistance = apex - state.position.y
                if (fallDistance > config.maxSafeFallDistance) {
                    return Evaluation(null, TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
                }
                apex = state.position.y
            } else {
                apex = maxOf(apex, state.position.y)
            }

            // A head bonk is a rising body meeting a ceiling; it kills the arc family,
            // so it must be told apart from an ordinary landing.
            val before = if (index == 0) rollout.initialState else rollout.frames[index - 1].state
            if (blocker == null && state.verticalCollision && before.velocity.y > 0.0 && !state.onGround) {
                blocker = TrajectoryDiagnostic.HeadBonk(index, state.position)
            }

            // Whether a wall matters depends on whether the body was *walking* into it.
            //
            // Grounded: the ground path is blocked. Fatal -- and it is exactly the
            // signal that a launch might be needed, so it seeds jump discovery.
            //
            // Airborne: a rising jump scrapes the very lip it is clearing. Vanilla
            // slides along it and the arc completes. Vetoing that would make every
            // rising jump uncertifiable, which is how the step-up route broke the
            // moment the coarse layer started preferring a rising jump over a step.
            if (state.horizontalCollision && state.onGround) {
                return Evaluation(null, TrajectoryDiagnostic.HorizontalCollision(index, state.position))
            }
            if (state.position.y < floor) {
                return Evaluation(null, TrajectoryDiagnostic.FellBelowRoute(index, floor - state.position.y))
            }
            val deviation = horizontalDistanceToPolyline(state.position.x, state.position.z, nodes)
            if (deviation > config.maxCorridorDeviation) {
                return Evaluation(null, TrajectoryDiagnostic.LeftCorridor(index, deviation))
            }

            val atGoal = hypot(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
                kotlin.math.abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
            val stopped = state.velocity.horizontalLength() <= config.stoppedSpeed
            stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
            if (stable >= config.stableStopFrames) return Evaluation(index, null)
        }

        (rollout.termination as? TrajectoryRolloutTermination.Rejected)?.let { rejected ->
            return Evaluation(
                null,
                TrajectoryDiagnostic.UnsupportedPhysics(rejected.frame, rejected.failure.message ?: "unsupported"),
            )
        }

        // Never got there. If something blocked it on the way, that is the actionable
        // cause; a bare "did not stop" is only the truth when nothing was in the way.
        blocker?.let { return Evaluation(null, it) }

        val final = rollout.finalState
        return Evaluation(
            null,
            TrajectoryDiagnostic.NoStop(
                frame = rollout.frames.size,
                goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                speed = final.velocity.horizontalLength(),
            ),
        )
    }

    private class CorridorWalkingProgram(
        stanceNodes: List<Stance>,
        private val parameters: WalkingSeedParameters,
        private val maxYawChange: Double,
    ) : ControlProgram {
        private val nodes = stanceNodes.map { it.center() }
        /**
         * Rises this program will jump on its step-up schedule.
         *
         * Empty when no lead distance was chosen: the route's rises are then a gap
         * launch's business, not the step-up schedule's. Deriving these from node
         * geometry alone left `nextRise` stuck at zero -- `shouldJump` bails before
         * advancing it -- so a rise stayed permanently "pending" and the brake, which
         * waits for pending rises, never latched. The body then walked at full
         * throttle through the goal forever.
         */
        private val rises = if (parameters.stepUpJumpLeadDistance == null) {
            emptyList()
        } else {
            stanceNodes.zipWithNext().mapIndexedNotNull { index, (from, to) ->
                index.takeIf { to.y > from.y }
            }
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
            // A gap launch is a *discovered* frame, not a geometric lead: backtracking
            // over a failed walk is what found it, so it is replayed by index.
            val gapLaunch = parameters.gapLaunchFrame == frame && observed.onGround
            val jump = shouldJump(observed) || gapLaunch

            // Brake on distance remaining *along the route*, not straight-line
            // distance to the goal: around an obstacle the player can be a stride
            // from the goal as the crow flies while most of the path is still
            // ahead, and braking there stalls short of the corner. A pending rise
            // also holds the brake off -- a coasting player has no momentum to
            // clear a step-up, so a rise on the final edge could never launch.
            val risePending = nextRise < rises.size
            val gapPending = parameters.gapLaunchFrame != null && frame <= parameters.gapLaunchFrame
            if (!risePending && !gapPending &&
                remainingPathDistance(observed) <= parameters.brakeDistance
            ) braking = true
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

    /** Below the lowest route node by this much means the body left the route downward. */
    private const val FALL_TOLERANCE = 0.6
}
