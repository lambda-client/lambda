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

object CoarseMoveRates {
    const val SPRINT_BLOCKS_PER_TICK = 0.2806

    const val SPRINT_JUMP_BLOCKS_PER_TICK = 0.3640

    const val STEP_UP_BLOCKS_PER_TICK = 0.1000

    const val JUMP_AIR_TICKS = 12.0

    const val JUMP_RISE_TICKS = 2.0

    private const val MAX_FALL_TABLE_DEPTH = 12

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
    val lowerBoundTicks: Double,
    val readSet: Set<VoxelPos>,
    val jumpHint: JumpHint? = null,
)

class CoarseMoveCosts(
    val cardinalWalk: Double,
    val diagonalWalk: Double,
    val stepUp: Double,
    val walkOff: (depth: Int) -> Double,
    val jumpCandidate: (span: Int, verticalOffset: Int) -> Double,
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
    val maxJumpSpan: Int = 4,
    val maxJumpDrop: Int = 1,
    val maxDiagonalJumpSpan: Int = 2,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
        require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
        require(maxDiagonalJumpSpan >= 2) { "maxDiagonalJumpSpan must reach past the adjacent stance" }
    }
}
