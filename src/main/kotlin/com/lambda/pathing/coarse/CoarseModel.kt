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
    /** A jump candidate's lower bound depends on how far it reaches. */
    val jumpCandidate: (span: Int, rise: Int) -> Double,
) {
    init {
        validate("cardinalWalk", cardinalWalk)
        validate("diagonalWalk", diagonalWalk)
        validate("stepUp", stepUp)
    }

    fun jumpCandidateCost(span: Int, rise: Int): Double {
        require(span >= 2) { "a jump candidate must reach past the adjacent stance" }
        return jumpCandidate(span, rise).also { validate("jumpCandidate($span, $rise)", it) }
    }

    fun walkOffCost(depth: Int): Double {
        require(depth > 0) { "walk-off depth must be positive" }
        return walkOff(depth).also { validate("walkOff($depth)", it) }
    }

    private fun validate(name: String, value: Double) {
        require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
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
     * A span of 2 clears no hole at all -- a sprint walks that -- so anything under
     * 3 makes jump discovery pointless.
     */
    val maxJumpSpan: Int = 4,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
    }
}
