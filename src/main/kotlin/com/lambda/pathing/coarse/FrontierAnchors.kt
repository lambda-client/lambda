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
        if (from == goal || refusesOptimism(view, moves, goal)) return emptyMap()

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
            if (anchor == goal) continue
            val cost = optimisticCost(moves, anchor, goal)
            anchors.merge(anchor, cost, ::minOf)
        }
        return anchors
    }

    /**
     * Reachable frontier stances, found by searching rather than by ray-casting.
     *
     * The fan above places anchors along straight lines and deliberately ignores
     * obstacles, which is usually fine and sometimes fatal: when a ravine or a wall
     * severs every ray's landing from the body's own component, all three anchors are
     * unreachable, the backward search converges with the start at infinity, and a walk
     * that merely needed to follow the frontier sideways stops dead at the chunk border.
     *
     * This is the recovery: a bounded goal-ward best-first walk over the coarse edges the
     * body can actually take, collecting every stance it reaches that borders unstreamed
     * terrain. Any of them can carry the optimistic edge, and D* picks the best crossing
     * itself. Bounded because it runs eagerly forward -- it is a fallback for when the
     * cheap fan has failed, not the everyday path.
     */
    fun sweep(
        view: CoarseVoxelView,
        moves: SimpleMoveLibrary,
        from: Stance,
        goal: Stance,
        maxNodes: Int = SWEEP_MAX_NODES,
        maxAnchors: Int = SWEEP_MAX_ANCHORS,
        cancelled: () -> Boolean = { false },
    ): Map<Stance, Double> {
        if (from == goal || refusesOptimism(view, moves, goal)) return emptyMap()
        if (!moves.isStance(view, from)) return emptyMap()

        val anchors = HashMap<Stance, Double>()
        val visited = HashSet<Stance>()
        val queue = java.util.PriorityQueue<Stance>(compareBy { moves.heuristic(it, goal) })
        visited += from
        queue += from

        var expanded = 0
        while (queue.isNotEmpty() && expanded < maxNodes && anchors.size < maxAnchors) {
            if (cancelled()) break
            val node = queue.poll()
            expanded++
            if (node != goal && bordersUnknown(view, node)) anchors[node] = optimisticCost(moves, node, goal)
            // The full move set, not just ground edges: this recovery exists precisely for
            // terrain the walk-only fan cannot cross, and a start on jagged ground whose
            // every route to the frontier needs a jump would otherwise stay stranded.
            for (edge in moves.edgesFrom(view, node)) {
                if (visited.add(edge.to)) queue += edge.to
            }
        }
        return anchors
    }

    /** Whether a stance stands at the edge of the streamed world. */
    fun bordersUnknown(view: CoarseVoxelView, stance: Stance): Boolean =
        !view.isKnown(stance.x + 1, stance.y, stance.z) ||
            !view.isKnown(stance.x - 1, stance.y, stance.z) ||
            !view.isKnown(stance.x, stance.y, stance.z + 1) ||
            !view.isKnown(stance.x, stance.y, stance.z - 1)

    /**
     * Whether the terrain that decides the goal's standability has streamed.
     *
     * This, not "is the goal a stance", is what gates optimism. The two disagree exactly
     * where it hurts: a goal in *streamed* terrain that is not standable -- named at a
     * carpet's own cell, or floating in mid-air -- used to fall through the stance test
     * into the optimistic machinery, which happily bridged an edge to a fiction no chunk
     * arrival would ever retire. A resolved goal answers for itself: it routes directly
     * or it is refused, and optimism is reserved for terrain the client has not seen.
     */
    fun goalResolved(view: CoarseVoxelView, goal: Stance): Boolean =
        view.isKnown(goal.x, goal.y - 1, goal.z) &&
            view.isKnown(goal.x, goal.y, goal.z) &&
            view.isKnown(goal.x, goal.y + 1, goal.z)

    /**
     * Whether the goal itself rules optimism out.
     *
     * A goal whose deciding terrain is streamed and which nothing can stand in -- a
     * carpet's own cell, a point in mid-air -- is unreachable as a matter of fact, and
     * bridging fiction to it would send the walk chasing a place that can never be
     * stood in. That is the only thing goal resolution may veto: a resolved goal that
     * IS standable still needs anchors, because a retained journey re-planned from
     * outside streamed range must bridge the unstreamed middle even though the goal's
     * own chunks are long known.
     */
    fun refusesOptimism(view: CoarseVoxelView, moves: SimpleMoveLibrary, goal: Stance): Boolean =
        goalResolved(view, goal) && !moves.isStance(view, goal)

    /**
     * What crossing the unstreamed remainder is charged.
     *
     * The heuristic is a lower bound over every move the graph has, jumps included, so it
     * prices the unknown *below* the cost of walking the known. Under that pricing a route
     * is always better off stopping early and handing the rest to the fiction -- and since
     * the fan probes sideways as well as ahead, "early" can mean an anchor off to one side
     * that makes no progress toward the goal. That is how a walk toward a goal a hundred
     * blocks east ended up heading for the southern edge of the loaded chunks.
     *
     * Charged at the rate ordinary travel actually sustains instead, floored by the
     * heuristic so the edge never claims less than D* already believes. Being above the
     * admissible bound is safe here: this edge is a placeholder that real terrain retires,
     * and over-pricing it only makes the search prefer terrain it can actually see.
     */
    internal fun optimisticCost(moves: SimpleMoveLibrary, anchor: Stance, goal: Stance): Double {
        val dx = (goal.x - anchor.x).toDouble()
        val dz = (goal.z - anchor.z).toDouble()
        val realistic = hypot(dx, dz) * moves.sustainedTicksPerBlock
        return maxOf(moves.heuristic(anchor, goal), realistic)
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
        var frontier = false
        while (step <= maxSteps) {
            val x = from.x + (headingX * step).roundToInt()
            val z = from.z + (headingZ * step).roundToInt()
            if (!view.isKnown(x, level, z)) {
                if (stride == 1) {
                    frontier = true
                    break
                }
                step = maxOf(1, step - (stride - 1))
                stride = 1
                continue
            }
            surface(view, moves, x, z, level)?.let {
                level = it.y
                anchor = it
            }
            step += stride
        }
        // An anchor stands for terrain the client does not have. A march that ran out of
        // steps in fully streamed terrain found no frontier, and returning its last
        // surface anyway would bridge a fictional edge no chunk arrival ever retires --
        // that is how a goal walled off by real, streamed terrain must NOT get a route.
        return if (frontier) anchor?.takeIf { it != from } else null
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

    /** Far enough to cross a 32-chunk render distance and reach the streamed frontier. */
    private const val MAX_STEPS = 512

    /**
     * Recovery-sweep budget; the everyday fan never pays this. Ground edges only, so a
     * node costs a handful of cell reads -- the budget has to cover a whole streamed
     * component, because the frontier sits at its far edge by definition.
     */
    private const val SWEEP_MAX_NODES = 40_000

    private const val SWEEP_MAX_ANCHORS = 256
}
