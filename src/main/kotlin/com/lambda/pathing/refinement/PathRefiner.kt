/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.refinement

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.config.blocks.PathRefinementConfig
import com.lambda.pathing.movement.WalkingMovementModel
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationTick
import com.lambda.util.player.prediction.buildMovementSimulator
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Greedy post-processing pass that shortens a coarse D* Lite path.
 *
 * The planner intentionally keeps only short local edges. Refinement looks for
 * the farthest future waypoint that can replace a whole run of coarse nodes
 * while still being traversable. Four validators are used, selected by the
 * vertical delta between anchor and candidate:
 *
 * 1. **Flat walk sweep** — same-height shortcuts. Dense standing-position
 *    checks along the straight segment.
 *
 * 2. **Hybrid (sim + sweep)** — ±1 block vertical shortcuts. Simulates only
 *    the vertical transition (step down or jump up), then sweeps the flat
 *    remainder. Much cheaper than full simulation for long segments.
 *
 * 3. **Movement simulation** — non-flat shortcuts beyond ±1 block (future
 *    multi-step jumps). Full tick-by-tick simulation of the entire segment.
 *
 * 4. **Precheck reject** — fallback when simulation is disabled; endpoint
 *    standing checks fail for any vertical delta.
 */
object PathRefiner {
    private const val EPSILON = 1.0E-9
    private const val PLAYER_HALF_WIDTH = 0.3
    private const val MAX_DEBUG_ATTEMPTS = 96
    private const val MIN_SAMPLE_STEP = 1.0E-4

    fun SafeContext.refine(coarsePath: List<FastVector>, config: PathRefinementConfig): PathRefinementResult {
        val startedAt = System.nanoTime()
        val debugCollector = DebugCollector(MAX_DEBUG_ATTEMPTS)

        if (!config.enabled || coarsePath.size < 3) {
            return PathRefinementResult(
                path = coarsePath,
                stats = coarsePath.stats(
                    enabled = config.enabled,
                    refinedPath = coarsePath,
                    durationNanos = System.nanoTime() - startedAt,
                ),
                debug = debugCollector.build(),
            )
        }

        val clearanceMargin = config.clearanceMargin.coerceAtLeast(0.0)
        val maxLookahead = config.maxLookahead.coerceAtLeast(1)
        val maxChecks = config.maxChecks.coerceAtLeast(1)
        val refined = arrayListOf(coarsePath.first())

        var anchorIndex = 0
        var shortcutChecks = 0
        var skippedCandidates = 0
        var budgetExhausted = false

        while (anchorIndex < coarsePath.lastIndex) {
            if (shortcutChecks >= maxChecks) {
                budgetExhausted = true
                anchorIndex = commitAnchor(refined, coarsePath, anchorIndex + 1, anchorIndex)
                appendRemainder(refined, coarsePath, anchorIndex + 1)
                break
            }

            val anchor = coarsePath[anchorIndex]
            val furthestCandidate = (anchorIndex + maxLookahead).coerceAtMost(coarsePath.lastIndex)
            var committedIndex = (anchorIndex + 1).coerceAtMost(coarsePath.lastIndex)
            var foundShortcut = false

            if (!config.useHybridRefinement) {
                val anchorY = anchor.y
                val nextY = coarsePath[anchorIndex + 1].y
                if (anchorY != nextY) {
                    val allForwardSameY = (anchorIndex + 1..furthestCandidate).all { coarsePath[it].y == nextY }
                    if (allForwardSameY) {
                        skippedCandidates += furthestCandidate - anchorIndex
                        debugCollector.recordSkipBatch(furthestCandidate - anchorIndex)
                        anchorIndex = commitAnchor(refined, coarsePath, anchorIndex + 1, anchorIndex)
                        continue
                    }
                }
            }

            for (candidateIndex in furthestCandidate downTo (anchorIndex + 1)) {
                if (shortcutChecks >= maxChecks) {
                    budgetExhausted = true
                    break
                }

                val candidate = coarsePath[candidateIndex]
                if (!isShortcutCandidate(anchor, candidate)) {
                    skippedCandidates++
                    debugCollector.recordSkipped()
                    continue
                }

                shortcutChecks++
                val evaluation = evaluateShortcut(anchor, candidate, config, clearanceMargin, coarsePath, anchorIndex)
                debugCollector.record(evaluation)

                if (evaluation.traversable) {
                    committedIndex = candidateIndex
                    foundShortcut = true
                    break
                }
            }

            anchorIndex = commitAnchor(
                refined = refined,
                coarsePath = coarsePath,
                requestedIndex = if (foundShortcut) committedIndex else anchorIndex + 1,
                previousAnchorIndex = anchorIndex,
            )

            if (budgetExhausted) {
                appendRemainder(refined, coarsePath, anchorIndex + 1)
                break
            }
        }

        if (!budgetExhausted && refined.last() != coarsePath.last()) {
            refined += coarsePath.last()
        }

        val outputPath = refined.simplifyCollinearSegments()
        return PathRefinementResult(
            path = outputPath,
            stats = coarsePath.stats(
                enabled = true,
                refinedPath = outputPath,
                shortcutChecks = shortcutChecks,
                skippedCandidates = skippedCandidates,
                budgetExhausted = budgetExhausted,
                durationNanos = System.nanoTime() - startedAt,
            ),
            debug = debugCollector.build(),
        )
    }

    private fun isShortcutCandidate(start: FastVector, end: FastVector): Boolean {
        val dx = (end.x - start.x).toDouble()
        val dz = (end.z - start.z).toDouble()
        return dx * dx + dz * dz > EPSILON
    }

    private fun SafeContext.evaluateShortcut(
        start: FastVector,
        end: FastVector,
        refinement: PathRefinementConfig,
        horizontalClearanceMargin: Double,
        coarsePath: List<FastVector>,
        anchorIndex: Int,
    ): ShortcutEvaluation {
        val startPos = start.toFeetCenter()
        val endPos = end.toFeetCenter()

        if (startPos.squaredDistanceTo(endPos) <= EPSILON) {
            return singleAttemptEvaluation(
                traversable = true,
                start = start,
                end = end,
                profile = ShortcutProfile.Precheck,
                reason = ShortcutFailureReason.Accepted,
            )
        }

        if (!isBlockTraversable(startPos.flooredBlockPos, horizontalClearanceMargin)) {
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Precheck,
                reason = ShortcutFailureReason.StartBlocked,
            )
        }

        if (!isBlockTraversable(endPos.flooredBlockPos, horizontalClearanceMargin)) {
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Precheck,
                reason = ShortcutFailureReason.EndBlocked,
            )
        }

        val dy = end.y - start.y
        return when {
            dy == 0 -> evaluateFlatWalkShortcut(start, end, startPos, endPos, horizontalClearanceMargin, refinement)
            abs(dy) <= refinement.hybridVerticalStepLimit && refinement.useHybridRefinement && isAtStepBoundary(anchorIndex, coarsePath) ->
                evaluateHybridShortcut(start, end, startPos, endPos, horizontalClearanceMargin, refinement)
            else -> singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Precheck,
                reason = ShortcutFailureReason.UnplannedRise,
            )
        }
    }

    /**
     * True when the anchor node is at a vertical step boundary in the coarse
     * path — its y differs from the next node. Only step-boundary anchors are
     * eligible for hybrid refinement. Without this gate, flat-ground anchors
     * would produce shortcuts that begin before the stairs and create
     * impossible steering angles that require flight.
     */
    private fun isAtStepBoundary(anchorIndex: Int, coarsePath: List<FastVector>): Boolean {
        if (anchorIndex >= coarsePath.lastIndex) return false
        return coarsePath[anchorIndex].y != coarsePath[anchorIndex + 1].y
    }

    private fun SafeContext.isBlockTraversable(pos: BlockPos, clearanceMargin: Double): Boolean =
        with(WalkingMovementModel) {
            isStandingPositionTraversable(Vec3d.ofBottomCenter(pos), clearanceMargin)
        }

    private fun SafeContext.evaluateFlatWalkShortcut(
        start: FastVector,
        end: FastVector,
        startPos: Vec3d,
        endPos: Vec3d,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
    ): ShortcutEvaluation {
        val sampleStep = refinement.flatWalkSampleStep.coerceAtLeast(MIN_SAMPLE_STEP)
        val distance = horizontalDistance(startPos, endPos)
        val sampleCount = ceil(distance / sampleStep).toInt().coerceAtLeast(1)
        val anchorY = startPos.flooredBlockPos.y
        val halfWidth = PLAYER_HALF_WIDTH + horizontalClearanceMargin.coerceAtLeast(0.0)

        for (sampleIndex in 1 until sampleCount) {
            val sampleT = sampleIndex.toDouble() / sampleCount.toDouble()
            val samplePos = interpolate(startPos, endPos, sampleT)

            val xMin = floorInt(samplePos.x - halfWidth)
            val xMax = floorInt(samplePos.x + halfWidth)
            val zMin = floorInt(samplePos.z - halfWidth)
            val zMax = floorInt(samplePos.z + halfWidth)

            var blocked = false
            for (bx in xMin..xMax) {
                for (bz in zMin..zMax) {
                    if (!isBlockTraversable(BlockPos(bx, anchorY, bz), horizontalClearanceMargin)) {
                        blocked = true
                        break
                    }
                }
                if (blocked) break
            }
            if (blocked) {
                return singleAttemptEvaluation(
                    traversable = false,
                    start = start,
                    end = end,
                    profile = ShortcutProfile.FlatWalk,
                    reason = ShortcutFailureReason.SweepBlocked,
                    ticks = sampleIndex,
                    bestRemaining = horizontalDistance(samplePos, endPos),
                )
            }
        }

        return singleAttemptEvaluation(
            traversable = true,
            start = start,
            end = end,
            profile = ShortcutProfile.FlatWalk,
            reason = ShortcutFailureReason.Accepted,
            ticks = sampleCount,
            bestRemaining = 0.0,
        )
    }

    private fun SafeContext.evaluateHybridShortcut(
        start: FastVector,
        end: FastVector,
        startPos: Vec3d,
        endPos: Vec3d,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
    ): ShortcutEvaluation {
        val dy = end.y - start.y
        val segmentRotation = startPos.rotationTo(endPos)

        val simulator = buildMovementSimulator(
            initialState = MovementSimulationState.at(
                player = player,
                position = startPos,
                rotation = segmentRotation,
                velocity = Vec3d.ZERO,
                onGround = true,
                isSprinting = false,
            ),
            inputProvider = { MovementSimulationInput() },
        ).also { it.skipEntityCollisions = true }

        val maxPhaseATicks = min(refinement.hybridSimMaxTicks.coerceAtLeast(10), 80)
        val corridorRadius = PLAYER_HALF_WIDTH + horizontalClearanceMargin + refinement.simulationCorridorMargin.coerceAtLeast(0.0)
        val targetY = end.y

        val transitionTick = if (dy < 0) {
            simulateWalkDownTransition(this, simulator, startPos, endPos, targetY, maxPhaseATicks, corridorRadius, refinement, horizontalClearanceMargin)
        } else {
            simulateJumpUpTransition(this, simulator, startPos, endPos, targetY, maxPhaseATicks, corridorRadius, refinement, horizontalClearanceMargin)
        }

        if (transitionTick == null) {
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Hybrid,
                reason = ShortcutFailureReason.HybridStepBlocked,
            )
        }

        // The vertical transition must complete within a physically plausible
        // horizontal distance from the start. A single-block step-up or
        // step-down reaches at most ~3 blocks forward; if the sim walked
        // further the anchor cannot serve as the actual takeoff point.
        val transitionDist = horizontalDistance(startPos, transitionTick.position)
        if (transitionDist > 3.5) {
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Hybrid,
                reason = ShortcutFailureReason.HybridStepBlocked,
            )
        }

        val sweepResult = evaluateSweepSubsegment(
            fromPos = transitionTick.position,
            toPos = endPos,
            supportY = targetY,
            sampleStep = refinement.hybridFlatSampleStep.coerceAtLeast(MIN_SAMPLE_STEP),
            horizontalClearanceMargin = horizontalClearanceMargin,
        )

        if (!sweepResult) {
            return singleAttemptEvaluation(
                traversable = false,
                start = start,
                end = end,
                profile = ShortcutProfile.Hybrid,
                reason = ShortcutFailureReason.HybridSweepBlocked,
            )
        }

        return singleAttemptEvaluation(
            traversable = true,
            start = start,
            end = end,
            profile = ShortcutProfile.Hybrid,
            reason = ShortcutFailureReason.Accepted,
        )
    }

    private fun simulateWalkDownTransition(
        context: SafeContext,
        simulator: MovementSimulator,
        startPos: Vec3d,
        endPos: Vec3d,
        targetY: Int,
        maxTicks: Int,
        corridorRadius: Double,
        refinement: PathRefinementConfig,
        horizontalClearanceMargin: Double,
    ): MovementSimulationTick? {
        for (tick in 0 until maxTicks) {
            val current = simulator.lastTick

            if (current.onGround && current.position.flooredBlockPos.y == targetY) {
                if (with(context) { isBlockTraversable(current.position.flooredBlockPos, horizontalClearanceMargin) }) {
                    return current
                }
            }

            val failureReason = current.classifyFailure(
                startPos = startPos,
                endPos = endPos,
                corridorRadius = corridorRadius,
                profile = ShortcutProfile.Hybrid,
                refinement = refinement,
            )
            if (failureReason != null) {
                if (failureReason == ShortcutFailureReason.UnplannedRise ||
                    failureReason == ShortcutFailureReason.UnplannedDrop
                ) {
                    // Expected during vertical transition phase
                } else {
                    return null
                }
            }

            val nextInput = MovementSimulationInput(
                forward = 1.0,
                strafe = 0.0,
                jump = false,
                sneak = false,
                sprint = false,
                useItemSlowdown = false,
                rotation = startPos.rotationTo(endPos),
            )
            simulator.tickMovement(nextInput)
        }
        return null
    }

    private fun simulateJumpUpTransition(
        context: SafeContext,
        simulator: MovementSimulator,
        startPos: Vec3d,
        endPos: Vec3d,
        targetY: Int,
        maxTicks: Int,
        corridorRadius: Double,
        refinement: PathRefinementConfig,
        horizontalClearanceMargin: Double,
    ): MovementSimulationTick? {
        for (jumpTick in 0..2) {
            simulator.reset(
                MovementSimulationState.at(
                    player = simulator.player,
                    position = startPos,
                    rotation = startPos.rotationTo(endPos),
                    velocity = Vec3d.ZERO,
                    onGround = true,
                    isSprinting = true,
                )
            )

            for (tick in 0 until maxTicks) {
                val current = simulator.lastTick

                if (current.onGround && current.position.flooredBlockPos.y == targetY) {
                    if (with(context) { isBlockTraversable(current.position.flooredBlockPos, horizontalClearanceMargin) }) {
                        return current
                    }
                }

                val failureReason = current.classifyFailure(
                    startPos = startPos,
                    endPos = endPos,
                    corridorRadius = corridorRadius,
                    profile = ShortcutProfile.Hybrid,
                    refinement = refinement,
                )
                if (failureReason != null) {
                    if (failureReason == ShortcutFailureReason.UnplannedRise ||
                        failureReason == ShortcutFailureReason.UnplannedDrop ||
                        failureReason == ShortcutFailureReason.HorizontalCollision
                    ) {
                        // Normal during jump arc transitions
                    } else {
                        break
                    }
                }

                val nextInput = MovementSimulationInput(
                    forward = 1.0,
                    strafe = 0.0,
                    jump = tick == jumpTick,
                    sneak = false,
                    sprint = true,
                    useItemSlowdown = false,
                    rotation = startPos.rotationTo(endPos),
                )
                simulator.tickMovement(nextInput)
            }
        }
        return null
    }

    private fun SafeContext.evaluateSweepSubsegment(
        fromPos: Vec3d,
        toPos: Vec3d,
        supportY: Int,
        sampleStep: Double,
        horizontalClearanceMargin: Double,
    ): Boolean {
        val distance = horizontalDistance(fromPos, toPos)
        if (distance <= EPSILON) return true

        val sampleCount = ceil(distance / sampleStep).toInt().coerceAtLeast(1)
        val halfWidth = PLAYER_HALF_WIDTH + horizontalClearanceMargin.coerceAtLeast(0.0)

        for (sampleIndex in 1 until sampleCount) {
            val samplePos = interpolate(fromPos, toPos, sampleIndex.toDouble() / sampleCount.toDouble())
            val xMin = floorInt(samplePos.x - halfWidth)
            val xMax = floorInt(samplePos.x + halfWidth)
            val zMin = floorInt(samplePos.z - halfWidth)
            val zMax = floorInt(samplePos.z + halfWidth)

            var blocked = false
            for (bx in xMin..xMax) {
                for (bz in zMin..zMax) {
                    if (!isBlockTraversable(BlockPos(bx, supportY, bz), horizontalClearanceMargin)) {
                        blocked = true
                        break
                    }
                }
                if (blocked) break
            }
            if (blocked) return false
        }

        return true
    }

    private fun floorInt(value: Double): Int {
        val i = value.toInt()
        return if (value < i) i - 1 else i
    }

    private fun SafeContext.evaluateSimulatedShortcut(
        start: FastVector,
        end: FastVector,
        startPos: Vec3d,
        endPos: Vec3d,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
    ): ShortcutEvaluation {
        val attempts = ArrayList<ShortcutAttemptDebug>()
        val segmentDistance = horizontalDistance(startPos, endPos)
        val segmentRotation = startPos.rotationTo(endPos)
        val simulator = buildMovementSimulator(
            initialState = MovementSimulationState.at(
                player = player,
                position = startPos,
                rotation = segmentRotation,
                velocity = Vec3d.ZERO,
                onGround = true,
                isSprinting = false,
            ),
            inputProvider = { MovementSimulationInput() },
        ).also { it.skipEntityCollisions = true }

        for (profile in shortcutProfiles(startPos, endPos)) {
            val result = simulateProfile(
                simulator = simulator,
                startPos = startPos,
                endPos = endPos,
                segmentDistance = segmentDistance,
                segmentRotation = segmentRotation,
                horizontalClearanceMargin = horizontalClearanceMargin,
                refinement = refinement,
                profile = profile,
            )
            val attempt = result.toAttempt(start, end, profile)
            attempts += attempt
            if (result.accepted) {
                return ShortcutEvaluation(traversable = true, attempts = attempts)
            }
        }

        return ShortcutEvaluation(traversable = false, attempts = attempts)
    }

    private fun commitAnchor(
        refined: MutableList<FastVector>,
        coarsePath: List<FastVector>,
        requestedIndex: Int,
        previousAnchorIndex: Int,
    ): Int {
        val nextAnchorIndex = requestedIndex.coerceIn(previousAnchorIndex + 1, coarsePath.lastIndex)
        val nextAnchor = coarsePath[nextAnchorIndex]
        if (refined.last() != nextAnchor) {
            refined += nextAnchor
        }
        return nextAnchorIndex
    }

    private fun appendRemainder(refined: MutableList<FastVector>, coarsePath: List<FastVector>, startIndex: Int) {
        for (index in startIndex..coarsePath.lastIndex) {
            val node = coarsePath[index]
            if (refined.lastOrNull() != node) {
                refined += node
            }
        }
    }

    private fun FastVector.toFeetCenter(): Vec3d = Vec3d.ofBottomCenter(toBlockPos())

    private fun SafeContext.simulateProfile(
        simulator: MovementSimulator,
        startPos: Vec3d,
        endPos: Vec3d,
        segmentDistance: Double,
        segmentRotation: Rotation,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
        profile: ShortcutProfile,
    ): ShortcutSimulationResult {
        simulator.reset(
            MovementSimulationState.at(
                player = player,
                position = startPos,
                rotation = segmentRotation,
                velocity = Vec3d.ZERO,
                onGround = true,
                isSprinting = profile.sprint,
            )
        )

        val corridorRadius = PLAYER_HALF_WIDTH + horizontalClearanceMargin + refinement.simulationCorridorMargin.coerceAtLeast(0.0)
        val reachThreshold = refinement.reachThreshold.coerceAtLeast(EPSILON)
        val maxTicks = min(
            refinement.simulationMaxTicks.coerceAtLeast(1),
            estimateSimulationTicks(segmentDistance, profile, refinement),
        )
        val stagnationLimit = refinement.simulationStagnationTicks.coerceAtLeast(1)

        var bestRemaining = segmentDistance
        var bestTick = simulator.lastTick
        var stagnantTicks = 0

        repeat(maxTicks) { tick ->
            val current = simulator.lastTick
            val remaining = horizontalDistance(current.position, endPos)

            if (remaining + refinement.stagnationProgressEpsilon < bestRemaining) {
                bestRemaining = remaining
                bestTick = current
                stagnantTicks = 0
            } else {
                stagnantTicks++
            }

            if (reachedTarget(current, endPos, horizontalClearanceMargin, refinement)) {
                return ShortcutSimulationResult(true, ShortcutFailureReason.Accepted, tick, bestRemaining)
            }

            val failureReason = current.classifyFailure(
                startPos = startPos,
                endPos = endPos,
                corridorRadius = corridorRadius,
                profile = profile,
                refinement = refinement,
            )
            if (failureReason != null) {
                if (softReached(current, endPos, horizontalClearanceMargin, refinement) ||
                    softReached(bestTick, endPos, horizontalClearanceMargin, refinement)
                ) {
                    return ShortcutSimulationResult(true, ShortcutFailureReason.Accepted, tick, bestRemaining)
                }
                return ShortcutSimulationResult(false, failureReason, tick, bestRemaining)
            }

            if (stagnantTicks >= stagnationLimit) {
                return if (softReached(bestTick, endPos, horizontalClearanceMargin, refinement)) {
                    ShortcutSimulationResult(true, ShortcutFailureReason.Accepted, tick, bestRemaining)
                } else {
                    ShortcutSimulationResult(false, ShortcutFailureReason.Stagnated, tick, bestRemaining)
                }
            }

            val steeringTarget = if (profile.useLookahead) {
                current.lookaheadTarget(startPos, endPos, refinement.simulationLookaheadDistance.coerceAtLeast(0.0))
            } else {
                endPos
            }

            val nextInput = MovementSimulationInput(
                forward = current.forwardInputFor(endPos, reachThreshold, profile, refinement),
                strafe = 0.0,
                jump = profile.shouldJump(tick),
                sneak = false,
                sprint = profile.sprint,
                useItemSlowdown = false,
                rotation = if (profile.useLookahead) current.position.rotationTo(steeringTarget) else segmentRotation,
            )
            simulator.tickMovement(nextInput)
        }

        return if (
            reachedTarget(simulator.lastTick, endPos, horizontalClearanceMargin, refinement) ||
            softReached(bestTick, endPos, horizontalClearanceMargin, refinement)
        ) {
            ShortcutSimulationResult(true, ShortcutFailureReason.Accepted, maxTicks, bestRemaining)
        } else {
            ShortcutSimulationResult(
                accepted = false,
                reason = ShortcutFailureReason.Timeout,
                ticks = maxTicks,
                bestRemaining = horizontalDistance(simulator.lastTick.position, endPos),
            )
        }
    }

    private fun SafeContext.reachedTarget(
        tick: MovementSimulationTick,
        endPos: Vec3d,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
    ): Boolean {
        val standsSafely = with(WalkingMovementModel) {
            isStandingPositionTraversable(tick.position, horizontalClearanceMargin)
        }
        if (!tick.onGround || !standsSafely) {
            return false
        }

        val endBlock = endPos.flooredBlockPos
        if (tick.position.flooredBlockPos == endBlock) {
            return true
        }

        return horizontalDistance(tick.position, endPos) <= refinement.reachThreshold &&
            abs(tick.position.y - endPos.y) <= refinement.verticalReachThreshold
    }

    private fun SafeContext.softReached(
        tick: MovementSimulationTick,
        endPos: Vec3d,
        horizontalClearanceMargin: Double,
        refinement: PathRefinementConfig,
    ): Boolean {
        val endBlock = endPos.flooredBlockPos
        val closeEnough = horizontalDistance(tick.position, endPos) <= max(
            refinement.softReachThreshold,
            refinement.reachThreshold * refinement.softReachMultiplier,
        )
        val inTargetBlock = tick.position.flooredBlockPos == endBlock
        val lowVelocity = tick.horizontalSpeed() <= refinement.softReachMaxSpeed
        val verticallyClose = abs(tick.position.y - endPos.y) <= refinement.softVerticalReachThreshold

        if (!(inTargetBlock || closeEnough) || !lowVelocity || !verticallyClose) {
            return false
        }

        return with(WalkingMovementModel) {
            isStandingPositionTraversable(endPos, horizontalClearanceMargin)
        }
    }

    private fun MovementSimulationTick.classifyFailure(
        startPos: Vec3d,
        endPos: Vec3d,
        corridorRadius: Double,
        profile: ShortcutProfile,
        refinement: PathRefinementConfig,
    ): ShortcutFailureReason? {
        if (simulator.state.horizontalCollision) {
            return ShortcutFailureReason.HorizontalCollision
        }

        val progress = horizontalProgress(position, startPos, endPos)
        if (progress < -refinement.backtrackTolerance) {
            return ShortcutFailureReason.Backtracked
        }

        val segmentLength = horizontalDistance(startPos, endPos).coerceAtLeast(EPSILON)
        val overshootDistance = ((progress - 1.0).coerceAtLeast(0.0)) * segmentLength
        if (
            overshootDistance > refinement.maxOvershootDistance &&
            horizontalDistance(position, endPos) > refinement.maxOvershootRemainingDistance
        ) {
            return ShortcutFailureReason.Overshot
        }

        if (distanceToSegmentXZ(position, startPos, endPos) > corridorRadius) {
            return ShortcutFailureReason.LeftCorridor
        }

        if (!profile.allowRise && position.y > startPos.y + refinement.maxUnplannedRise) {
            return ShortcutFailureReason.UnplannedRise
        }

        if (!profile.allowDrop && position.y < min(startPos.y, endPos.y) - refinement.maxUnplannedDrop) {
            return ShortcutFailureReason.UnplannedDrop
        }

        return null
    }

    private fun MovementSimulationTick.lookaheadTarget(
        startPos: Vec3d,
        endPos: Vec3d,
        lookaheadDistance: Double,
    ): Vec3d {
        val progress = horizontalProgress(position, startPos, endPos)
        val segmentLength = horizontalDistance(startPos, endPos)
        if (segmentLength <= EPSILON) {
            return endPos
        }

        val lookaheadProgress = (progress + lookaheadDistance / segmentLength).coerceIn(0.0, 1.0)
        return interpolate(startPos, endPos, lookaheadProgress)
    }

    private fun MovementSimulationTick.forwardInputFor(
        endPos: Vec3d,
        reachThreshold: Double,
        profile: ShortcutProfile,
        refinement: PathRefinementConfig,
    ): Double {
        val remaining = horizontalDistance(position, endPos)
        if (position.flooredBlockPos == endPos.flooredBlockPos) {
            return 0.0
        }

        val brakingWindow = max(
            refinement.minBrakingWindow,
            horizontalSpeed() * if (profile.sprint) refinement.sprintBrakingTicks else refinement.walkBrakingTicks,
        )

        return when {
            remaining <= reachThreshold -> 0.0
            remaining <= brakingWindow * 0.35 -> if (profile.sprint) 0.35 else 0.5
            remaining <= brakingWindow * 0.6 -> if (profile.sprint) 0.55 else 0.7
            remaining <= brakingWindow -> if (profile.sprint) 0.8 else 0.9
            else -> 1.0
        }
    }

    private fun MovementSimulationTick.horizontalSpeed(): Double {
        val vx = velocity.x
        val vz = velocity.z
        return sqrt(vx * vx + vz * vz)
    }

    private fun estimateSimulationTicks(
        horizontalDistance: Double,
        profile: ShortcutProfile,
        refinement: PathRefinementConfig,
    ): Int {
        val expectedSpeed = when {
            profile.jumpTick != null && profile.sprint -> refinement.expectedSprintJumpSpeed
            profile.jumpTick != null -> refinement.expectedJumpSpeed
            profile.sprint -> refinement.expectedSprintWalkSpeed
            else -> refinement.expectedWalkSpeed
        }.coerceAtLeast(EPSILON)

        return max(
            refinement.minSimulationTicks.coerceAtLeast(1),
            ceil(horizontalDistance / expectedSpeed).toInt() + refinement.simulationTickBudgetPadding.coerceAtLeast(0),
        )
    }

    private fun shortcutProfiles(startPos: Vec3d, endPos: Vec3d): List<ShortcutProfile> {
        val verticalDelta = endPos.y - startPos.y
        return buildList {
            add(ShortcutProfile.Walk)
            add(ShortcutProfile.SprintWalk)

            if (verticalDelta < -EPSILON) {
                add(ShortcutProfile.DropWalk)
                add(ShortcutProfile.SprintDropWalk)
            }

            if (verticalDelta > EPSILON) {
                add(ShortcutProfile.JumpNow)
                add(ShortcutProfile.SprintJumpOneTick)
                add(ShortcutProfile.SprintJumpTwoTicks)
            }
        }
    }

    private fun singleAttemptEvaluation(
        traversable: Boolean,
        start: FastVector,
        end: FastVector,
        profile: ShortcutProfile,
        reason: ShortcutFailureReason,
        ticks: Int = 0,
        bestRemaining: Double = 0.0,
    ) = ShortcutEvaluation(
        traversable = traversable,
        attempts = listOf(
            ShortcutAttemptDebug(
                from = start,
                to = end,
                profile = profile.name,
                accepted = traversable,
                reason = reason.name,
                ticks = ticks,
                bestRemaining = bestRemaining,
            )
        ),
    )

    private fun ShortcutSimulationResult.toAttempt(
        start: FastVector,
        end: FastVector,
        profile: ShortcutProfile,
    ) = ShortcutAttemptDebug(
        from = start,
        to = end,
        profile = profile.name,
        accepted = accepted,
        reason = reason.name,
        ticks = ticks,
        bestRemaining = bestRemaining,
    )

    private fun List<FastVector>.stats(
        enabled: Boolean,
        refinedPath: List<FastVector>,
        shortcutChecks: Int = 0,
        skippedCandidates: Int = 0,
        budgetExhausted: Boolean = false,
        durationNanos: Long,
    ) = PathRefinementStats(
        enabled = enabled,
        coarseNodes = size,
        refinedNodes = refinedPath.size,
        coarseLength = pathLength(),
        refinedLength = refinedPath.pathLength(),
        shortcutChecks = shortcutChecks,
        skippedCandidates = skippedCandidates,
        budgetExhausted = budgetExhausted,
        durationMs = durationNanos / 1_000_000.0,
    )

    fun List<FastVector>.pathLength(): Double = zipWithNext { a, b ->
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        sqrt(dx * dx + dy * dy + dz * dz)
    }.sum()

    private fun List<FastVector>.simplifyCollinearSegments(): List<FastVector> {
        if (size <= 2) {
            return this
        }

        val simplified = ArrayList<FastVector>(size)
        simplified += first()

        for (index in 1 until lastIndex) {
            val previous = simplified.last()
            val current = this[index]
            val next = this[index + 1]
            if (!areCollinear(previous, current, next)) {
                simplified += current
            }
        }

        simplified += last()
        return simplified
    }

    private fun horizontalDistance(a: Vec3d, b: Vec3d): Double {
        val dx = b.x - a.x
        val dz = b.z - a.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun horizontalProgress(point: Vec3d, start: Vec3d, end: Vec3d): Double {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val lengthSquared = dx * dx + dz * dz
        if (lengthSquared <= EPSILON) {
            return 1.0
        }

        val px = point.x - start.x
        val pz = point.z - start.z
        return (px * dx + pz * dz) / lengthSquared
    }

    private fun distanceToSegmentXZ(point: Vec3d, start: Vec3d, end: Vec3d): Double {
        val projection = horizontalProgress(point, start, end).coerceIn(0.0, 1.0)
        val closestPoint = interpolate(start, end, projection)
        val dx = point.x - closestPoint.x
        val dz = point.z - closestPoint.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun interpolate(start: Vec3d, end: Vec3d, t: Double): Vec3d = Vec3d(
        start.x + (end.x - start.x) * t,
        start.y + (end.y - start.y) * t,
        start.z + (end.z - start.z) * t,
    )

    private fun areCollinear(a: FastVector, b: FastVector, c: FastVector): Boolean {
        if (a.y != b.y || b.y != c.y) return false

        val abx = b.x - a.x
        val aby = b.y - a.y
        val abz = b.z - a.z
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        val bcz = c.z - b.z

        val crossX = aby * bcz - abz * bcy
        val crossY = abz * bcx - abx * bcz
        val crossZ = abx * bcy - aby * bcx
        if (crossX != 0 || crossY != 0 || crossZ != 0) {
            return false
        }

        val dot = abx * bcx + aby * bcy + abz * bcz
        return dot >= 0
    }

    private data class ShortcutProfile(
        val name: String,
        val description: String,
        val sprint: Boolean,
        val jumpTick: Int?,
        val allowRise: Boolean,
        val allowDrop: Boolean,
        val useLookahead: Boolean,
    ) {
        fun shouldJump(tick: Int): Boolean = jumpTick == tick

        companion object {
            val Precheck = ShortcutProfile(
                name = "Precheck",
                description = "Only endpoint standing checks; no movement validation is needed yet.",
                sprint = false,
                jumpTick = null,
                allowRise = false,
                allowDrop = false,
                useLookahead = false,
            )

            val FlatWalk = ShortcutProfile(
                name = "FlatWalk",
                description = "Dense same-height standing-position sweep.",
                sprint = false,
                jumpTick = null,
                allowRise = false,
                allowDrop = false,
                useLookahead = false,
            )

            val Walk = ShortcutProfile(
                name = "Walk",
                description = "Grounded non-sprinting simulation that follows the segment directly.",
                sprint = false,
                jumpTick = null,
                allowRise = false,
                allowDrop = false,
                useLookahead = false,
            )

            val SprintWalk = ShortcutProfile(
                name = "SprintWalk",
                description = "Grounded sprinting simulation used for longer flat or shallow shortcuts.",
                sprint = true,
                jumpTick = null,
                allowRise = false,
                allowDrop = false,
                useLookahead = false,
            )

            val DropWalk = ShortcutProfile(
                name = "DropWalk",
                description = "Walking profile that allows planned descent but still rejects unexpected rises.",
                sprint = false,
                jumpTick = null,
                allowRise = false,
                allowDrop = true,
                useLookahead = false,
            )

            val SprintDropWalk = ShortcutProfile(
                name = "SprintDropWalk",
                description = "Sprinting drop profile for faster descending shortcuts.",
                sprint = true,
                jumpTick = null,
                allowRise = false,
                allowDrop = true,
                useLookahead = false,
            )

            val JumpNow = ShortcutProfile(
                name = "JumpNow",
                description = "Immediate jump on the first simulated tick with lookahead steering.",
                sprint = false,
                jumpTick = 0,
                allowRise = true,
                allowDrop = true,
                useLookahead = true,
            )

            val SprintJumpOneTick = ShortcutProfile(
                name = "SprintJump+1",
                description = "Sprint jump delayed by one tick to test a slightly later takeoff window.",
                sprint = true,
                jumpTick = 1,
                allowRise = true,
                allowDrop = true,
                useLookahead = true,
            )

            val SprintJumpTwoTicks = ShortcutProfile(
                name = "SprintJump+2",
                description = "Sprint jump delayed by two ticks for even later takeoff timing.",
                sprint = true,
                jumpTick = 2,
                allowRise = true,
                allowDrop = true,
                useLookahead = true,
            )

            val Hybrid = ShortcutProfile(
                name = "Hybrid",
                description = "Simulates the vertical transition then sweeps the flat remainder. Covers ±1 block steps up or down.",
                sprint = true,
                jumpTick = null,
                allowRise = true,
                allowDrop = true,
                useLookahead = false,
            )
        }
    }

    private data class ShortcutEvaluation(
        val traversable: Boolean,
        val attempts: List<ShortcutAttemptDebug>,
    )

    private data class ShortcutSimulationResult(
        val accepted: Boolean,
        val reason: ShortcutFailureReason,
        val ticks: Int,
        val bestRemaining: Double,
    )

    private class DebugCollector(private val maxRecentAttempts: Int) {
        private val acceptedByProfile = linkedMapOf<String, Int>()
        private val rejectedByReason = linkedMapOf<String, Int>()
        private val recentAttempts = ArrayDeque<ShortcutAttemptDebug>()
        private var acceptedCandidates = 0
        private var rejectedCandidates = 0
        private var skippedCandidates = 0
        private var profileAttempts = 0

        fun recordSkipped() {
            skippedCandidates++
        }

        fun recordSkipBatch(count: Int) {
            skippedCandidates += count
        }

        fun record(evaluation: ShortcutEvaluation) {
            if (evaluation.traversable) {
                acceptedCandidates++
            } else {
                rejectedCandidates++
            }

            for (attempt in evaluation.attempts) {
                profileAttempts++
                recentAttempts += attempt
                while (recentAttempts.size > maxRecentAttempts) {
                    recentAttempts.removeFirst()
                }
                if (attempt.accepted) {
                    acceptedByProfile.increment(attempt.profile)
                } else {
                    rejectedByReason.increment(attempt.reason)
                }
            }
        }

        fun build() = PathRefinementDebug(
            acceptedCandidates = acceptedCandidates,
            rejectedCandidates = rejectedCandidates,
            skippedCandidates = skippedCandidates,
            profileAttempts = profileAttempts,
            acceptedByProfile = acceptedByProfile.toMap(),
            rejectedByReason = rejectedByReason.toMap(),
            recentAttempts = recentAttempts.toList(),
        )

        private fun MutableMap<String, Int>.increment(key: String) {
            this[key] = (this[key] ?: 0) + 1
        }
    }
}
