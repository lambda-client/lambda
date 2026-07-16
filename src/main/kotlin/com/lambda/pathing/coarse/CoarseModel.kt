/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.world.VoxelPos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Measured per-move traversal rates, in blocks/tick, for 1.21.11.
 *
 * The uniform-`0.6` kinematic envelope prices every move at the same optimistic top
 * speed, which makes a climb look as cheap as a stride and a gap jump as cheap as a
 * walk -- so the coarse graph cannot tell a fast route from a slow one and picks
 * arbitrarily (the low-quality routes). These are the numbers the *executor actually
 * achieves*, so a cost built from them is an honest expected-time currency: the graph
 * then prefers genuinely faster lines. Provenance mirrors the prior planner's
 * `runClientGameTest` calibration; a stride re-measurement is in [[jump-physics-constants]].
 */
object CoarseMoveRates {
    /** Sustained flat sprint -- the pace of ordinary traversal. */
    const val SPRINT_BLOCKS_PER_TICK = 0.2806

    /** Sustained flat sprint-jumping (bunny-hop): ~30% faster than sprinting. */
    const val SPRINT_JUMP_BLOCKS_PER_TICK = 0.3640

    /** Sustained vertical rate of repeated 1-block jump-ups on a staircase. Climbing is slow. */
    const val STEP_UP_BLOCKS_PER_TICK = 0.1000

    /** Airborne ticks of a short gap jump, takeoff to touchdown. Longer spans amortise it. */
    const val JUMP_AIR_TICKS = 12.0

    /** Extra ticks for a jump that also climbs a block; a flat jump pays none. */
    const val JUMP_RISE_TICKS = 2.0

    private const val MAX_FALL_TABLE_DEPTH = 12

    /** Ticks to fall whole-block depths from rest, from vanilla's exact drag closed form. */
    private val FALL_TICKS = DoubleArray(MAX_FALL_TABLE_DEPTH + 1).also { table ->
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        while (depth <= MAX_FALL_TABLE_DEPTH) {
            tick++
            velocity = (velocity + 0.08) * 0.98
            fallen += velocity
            while (depth <= MAX_FALL_TABLE_DEPTH && fallen >= depth) {
                table[depth] = tick.toDouble()
                depth++
            }
        }
    }

    fun fallTicks(depth: Int): Double = FALL_TICKS[depth.coerceIn(1, MAX_FALL_TABLE_DEPTH)]
}

/** Centered, integer-foot-position node used only by the coarse search. */
data class Stance(val x: Int, val y: Int, val z: Int) {
    fun offset(dx: Int, dy: Int, dz: Int) = Stance(x + dx, y + dy, z + dz)
}

enum class CoarseMoveKind {
    WALK,
    STEP_UP,
    WALK_OFF,
    JUMP_CANDIDATE,
}

@JvmInline
value class MotionTemplateId(val value: Int)

data class CoarseEdgeId(val template: MotionTemplateId, val from: Stance)

data class CoarseEdge(
    val id: CoarseEdgeId,
    val from: Stance,
    val to: Stance,
    val kind: CoarseMoveKind,
    /** Correctness-bearing optimistic cost supplied by the cost model. */
    val lowerBoundTicks: Double,
    /** Every voxel whose traits were consulted to validate this edge. */
    val readSet: Set<VoxelPos>,
    /** Arc-probe prior for a jump candidate; a hint to the launch beam, never a promise. */
    val jumpHint: JumpHint? = null,
)

/**
 * Lower bounds for the enabled primitive library. These are deliberately an
 * injected policy: physics calibration may tighten them without changing
 * topology, while the template layer refuses negative/NaN/zero costs.
 */
class CoarseMoveCosts(
    val cardinalWalk: Double,
    val diagonalWalk: Double,
    val stepUp: Double,
    val walkOff: (depth: Int) -> Double,
    /** A cardinal jump candidate's lower bound depends on how far it reaches. */
    val jumpCandidate: (span: Int, verticalOffset: Int) -> Double,
    /**
     * A diagonal jump reaches `span` stances along *both* axes, so its true horizontal
     * distance is `span * sqrt(2)`, not `span`. Pricing it with [jumpCandidate] would
     * underprice it and make D* over-prefer diagonal jumps. Defaults to the cardinal
     * cost only for callers (tests) that predate diagonal jumps.
     */
    val diagonalJumpCandidate: (span: Int, verticalOffset: Int) -> Double = jumpCandidate,
) {
    init {
        validate("cardinalWalk", cardinalWalk)
        validate("diagonalWalk", diagonalWalk)
        validate("stepUp", stepUp)
    }

    fun jumpCandidateCost(span: Int, verticalOffset: Int): Double {
        require(span >= 2) { "a jump candidate must reach past the adjacent stance" }
        return jumpCandidate(span, verticalOffset).also {
            validate("jumpCandidate($span, $verticalOffset)", it)
        }
    }

    fun diagonalJumpCandidateCost(span: Int, verticalOffset: Int): Double {
        require(span >= 2) { "a jump candidate must reach past the adjacent stance" }
        return diagonalJumpCandidate(span, verticalOffset).also {
            validate("diagonalJumpCandidate($span, $verticalOffset)", it)
        }
    }

    fun walkOffCost(depth: Int): Double {
        require(depth > 0) { "walk-off depth must be positive" }
        return walkOff(depth).also { validate("walkOff($depth)", it) }
    }

    private fun validate(name: String, value: Double) {
        require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
    }

    companion object {
        /**
         * Costs in expected ticks, built from [CoarseMoveRates] rather than a single
         * optimistic top speed. This is the fix for low-quality routes: a climb now
         * costs its real ~10 ticks/block (not ~2), a gap jump its ~12 airborne ticks,
         * and a flat stride its sprinted ~3.6 -- so D* prefers the genuinely fastest
         * line instead of being indifferent between equal-lower-bound routes.
         *
         * A jump is priced by airborne time, which barely grows with span, so a longer
         * jump that clears more ground in one arc beats a short jump plus a walk -- the
         * "maximise jump distance" the reporter wants, falling out of the physics rather
         * than a hand-tuned toll. [transitionOverheadTicks] still adds a small per-edge
         * toll to lean off slaloms toward straight lines.
         */
        fun measured(transitionOverheadTicks: Double = 0.0): CoarseMoveCosts {
            require(transitionOverheadTicks >= 0.0 && transitionOverheadTicks.isFinite()) {
                "transitionOverheadTicks must be non-negative and finite: $transitionOverheadTicks"
            }
            val walk = 1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK
            val step = 1.0 / CoarseMoveRates.STEP_UP_BLOCKS_PER_TICK

            fun jump(horizontalDistance: Double, verticalOffset: Int): Double =
                max(CoarseMoveRates.JUMP_AIR_TICKS, horizontalDistance / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK) +
                    max(0, verticalOffset) * CoarseMoveRates.JUMP_RISE_TICKS

            return CoarseMoveCosts(
                cardinalWalk = walk + transitionOverheadTicks,
                diagonalWalk = sqrt(2.0) * walk + transitionOverheadTicks,
                stepUp = step + transitionOverheadTicks,
                walkOff = { depth -> max(walk, CoarseMoveRates.fallTicks(depth)) + 1.0 + transitionOverheadTicks },
                jumpCandidate = { span, vo -> jump(span.toDouble(), vo) + transitionOverheadTicks },
                diagonalJumpCandidate = { span, vo -> jump(span * sqrt(2.0), vo) + transitionOverheadTicks },
            )
        }
    }
}

data class SimpleMoveOptions(
    val allowDiagonal: Boolean = true,
    val allowStepUp: Boolean = true,
    val maxWalkOffDepth: Int = 3,
    val allowJumpCandidates: Boolean = true,
    /**
     * Furthest a candidate jump may reach, in stances.
     *
     * The mask is deliberately permissive (§5.1): a false positive costs one local
     * simulation, a false negative deletes topology the body could actually cross.
     * A span of 2 crosses one intermediate cell. A flat sprint often coasts over
     * that cell without jumping, but a vertically offset landing still needs the
     * connection to exist in coarse topology.
     */
    val maxJumpSpan: Int = 4,
    /** Deepest lower landing a jump candidate may target. */
    val maxJumpDrop: Int = 1,
    /**
     * Furthest a *diagonal* jump may reach along each axis, in stances. A span-2 diagonal
     * jump already covers 2.83 blocks, so this is kept below [maxJumpSpan]: longer diagonal
     * arcs are rarely feasible and each one is a template the lazy search may expand.
     */
    val maxDiagonalJumpSpan: Int = 2,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
        require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
        require(maxDiagonalJumpSpan >= 2) { "maxDiagonalJumpSpan must reach past the adjacent stance" }
    }
}
