/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the streamed world ends on the way to a goal it does not contain.
 *
 * A goal outside the streamed world is not reachable through terrain, because there
 * is no terrain there to model. Inventing some produces a second, fictional graph
 * whose every edge has to be retired again the moment a chunk arrives. Instead the
 * goal keeps its real position and is reached by one optimistic edge from the last
 * streamed stance on the way to it, priced at the admissible bound. Walking toward
 * that stance is what streams the next chunks, which moves the anchor and advances
 * the real graph behind it.
 */
object FrontierAnchors {
    /**
     * Probes outward from [from] toward [goal] along a fan of headings, and returns
     * the last stance each ray finds before the world stops being known, mapped to the
     * optimistic cost of covering the rest.
     *
     * An empty result means the goal needs no optimism: either it is streamed, or the
     * probe never left the stance it started on.
     */
    fun probe(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        goal: Stance,
        maxSteps: Int = MAX_STEPS,
    ): Map<Stance, Double> {
        if (from == goal || moves.isStance(view, goal)) return emptyMap()

        val dx = (goal.x - from.x).toDouble()
        val dz = (goal.z - from.z).toDouble()
        val length = hypot(dx, dz)
        if (length < 1.0) return emptyMap()

        val anchors = HashMap<Stance, Double>()
        for (degrees in FAN_DEGREES) {
            val radians = Math.toRadians(degrees)
            val headingX = (dx * cos(radians) - dz * sin(radians)) / length
            val headingZ = (dx * sin(radians) + dz * cos(radians)) / length
            val anchor = march(view, moves, from, headingX, headingZ, maxSteps) ?: continue
            val cost = moves.heuristic(anchor, goal)
            anchors.merge(anchor, cost, ::minOf)
        }
        return anchors
    }

    /**
     * Follows one heading over known terrain, tracking the surface, and stops at the
     * first column the client has not streamed. Terrain that merely blocks the way is
     * not a stopping condition: which stances are actually reachable is the search's
     * question, not the probe's.
     */
    private fun march(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        headingX: Double,
        headingZ: Double,
        maxSteps: Int,
    ): Stance? {
        var anchor: Stance? = null
        var level = from.y
        var step = 1
        // Every read here can cost the client a section copy, so cover the streamed
        // distance in strides and only walk block by block over the last one, where
        // the answer actually is.
        var stride = STRIDE
        while (step <= maxSteps) {
            val x = from.x + (headingX * step).roundToInt()
            val z = from.z + (headingZ * step).roundToInt()
            if (!view.isKnown(x, level, z)) {
                if (stride == 1) break
                step -= stride - 1
                stride = 1
                continue
            }
            surface(view, moves, x, z, level)?.let {
                level = it.y
                anchor = it
            }
            step += stride
        }
        return anchor?.takeIf { it != from }
    }

    /** The streamed stance closest to [level] in this column, if the column has one. */
    private fun surface(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        x: Int,
        z: Int,
        level: Int,
    ): Stance? {
        for (offset in 0..SURFACE_REACH) {
            val above = Stance(x, level + offset, z)
            if (view.isKnown(x, above.y, z) && moves.isStance(view, above)) return above
            if (offset == 0) continue
            val below = Stance(x, level - offset, z)
            if (view.isKnown(x, below.y, z) && moves.isStance(view, below)) return below
        }
        return null
    }

    /** Headings probed relative to the straight line at the goal, in degrees. */
    private val FAN_DEGREES = listOf(0.0, -35.0, 35.0)

    /** Vertical distance the probe will follow the surface between adjacent columns. */
    private const val SURFACE_REACH = 4

    private const val STRIDE = 8

    private const val MAX_STEPS = 320
}
