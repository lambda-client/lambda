/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import net.minecraft.util.math.Vec3d
import kotlin.math.hypot

data class CoarseKinematicEnvelope(
    val maxHorizontalBlocksPerTick: Double,
    val maxAscentBlocksPerTick: Double,
    val maxDescentBlocksPerTick: Double,
    val minimumTransitionTicks: Double = 1.0,
) {
    init {
        requirePositive("maxHorizontalBlocksPerTick", maxHorizontalBlocksPerTick)
        requirePositive("maxAscentBlocksPerTick", maxAscentBlocksPerTick)
        requirePositive("maxDescentBlocksPerTick", maxDescentBlocksPerTick)
        requirePositive("minimumTransitionTicks", minimumTransitionTicks)
    }

    fun contains(velocity: Vec3d): Boolean =
        velocity.horizontalLength() <= maxHorizontalBlocksPerTick + EPSILON &&
            velocity.y <= maxAscentBlocksPerTick + EPSILON &&
            velocity.y >= -maxDescentBlocksPerTick - EPSILON

    fun lowerBoundTicks(dx: Int, dy: Int, dz: Int): Double =
        lowerBoundTicks(hypot(dx.toDouble(), dz.toDouble()), dy)

    fun lowerBoundTicks(horizontalDistance: Double, dy: Int): Double {
        val horizontal = horizontalDistance / maxHorizontalBlocksPerTick
        val vertical = when {
            dy > 0 -> dy / maxAscentBlocksPerTick
            dy < 0 -> -dy / maxDescentBlocksPerTick
            else -> 0.0
        }
        return maxOf(minimumTransitionTicks, horizontal, vertical)
    }

    fun moveCosts(): CoarseMoveCosts = CoarseMoveCosts(
        cardinalWalk = lowerBoundTicks(1, 0, 0),
        diagonalWalk = lowerBoundTicks(1, 0, 1),
        stepUp = lowerBoundTicks(1, 1, 0),
        walkOff = { depth -> lowerBoundTicks(1, -depth, 0) },
        jumpCandidate = { distance, verticalOffset -> lowerBoundTicks(distance, verticalOffset) },
        drop = { span, depth -> lowerBoundTicks(span, -depth, 0) },
    )

    private fun requirePositive(name: String, value: Double) {
        require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
