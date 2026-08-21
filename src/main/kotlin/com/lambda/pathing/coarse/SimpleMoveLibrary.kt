/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.coarse.MotionTemplate.CellCondition
import com.lambda.pathing.coarse.MotionTemplate.Condition
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.VoxelPos
import kotlin.math.abs

class SimpleMoveLibrary private constructor(
    val templates: List<MotionTemplate>,
    private val readOffsets: Set<VoxelPos>,
    val heuristicCaps: HeuristicCaps,
) {
    private val minReadX = readOffsets.minOf(VoxelPos::x)
    private val maxReadX = readOffsets.maxOf(VoxelPos::x)
    private val minReadZ = readOffsets.minOf(VoxelPos::z)
    private val maxReadZ = readOffsets.maxOf(VoxelPos::z)

    data class HeuristicCaps(
        /** Cheapest lower-bound cost for one unmatched X or Z block. */
        val axisTicksPerBlock: Double,
        /** Cheapest lower-bound cost for changing X and Z by one together. */
        val diagonalTicksPerPair: Double,
        val ascentTicksPerBlock: Double,
        val descentTicksPerBlock: Double,
    )

    fun isStance(view: CoarseVoxelView, stance: Stance): Boolean {
        if (stance.y !in view.simulableStanceY) return false
        val support = view.voxel(stance.x, stance.y - 1, stance.z)
        return support.standableFullTop && !support.intrudesAbove &&
            view.voxel(stance.x, stance.y, stance.z).centerPassable &&
            view.voxel(stance.x, stance.y + 1, stance.z).centerPassable
    }

    fun edgesFrom(view: CoarseVoxelView, origin: Stance): List<CoarseEdge> {
        if (!isStance(view, origin)) return emptyList()
        return templates.mapNotNull { it.edge(view, origin) }
    }

    fun edgesTo(view: CoarseVoxelView, target: Stance): List<CoarseEdge> {
        if (!isStance(view, target)) return emptyList()
        return templates.mapNotNull { template ->
            val origin = target.offset(-template.dx, -template.dy, -template.dz)
            if (isStance(view, origin)) template.edge(view, origin) else null
        }
    }

    fun successorCosts(view: CoarseVoxelView, origin: Stance): Map<Stance, Double> =
        edgesFrom(view, origin).minimumCostsBy { it.to }

    fun predecessorCosts(view: CoarseVoxelView, target: Stance): Map<Stance, Double> =
        edgesTo(view, target).minimumCostsBy { it.from }

    fun heuristic(from: Stance, to: Stance): Double {
        val dx = abs(to.x - from.x)
        val dz = abs(to.z - from.z)
        val paired = minOf(dx, dz)
        val straight = maxOf(dx, dz) - paired
        val horizontal = paired * heuristicCaps.diagonalTicksPerPair.finiteOrZero() +
            straight * heuristicCaps.axisTicksPerBlock.finiteOrZero()
        val dy = to.y - from.y
        val vertical = when {
            dy > 0 -> dy * heuristicCaps.ascentTicksPerBlock.finiteOrZero()
            dy < 0 -> -dy * heuristicCaps.descentTicksPerBlock.finiteOrZero()
            else -> 0.0
        }
        return maxOf(horizontal, vertical)
    }

    fun affectedOrigins(changed: VoxelPos): Set<Stance> = buildSet(readOffsets.size) {
        for ((x, y, z) in readOffsets) {
            add(Stance(changed.x - x, changed.y - y, changed.z - z))
        }
    }

    /** Known origins whose possible edge reads overlap a refreshed chunk. */
    fun affectedOrigins(chunk: PathingChunk, candidates: Iterable<Stance>): Set<Stance> {
        val chunkMinX = chunk.x shl 4
        val chunkMaxX = chunkMinX + 15
        val chunkMinZ = chunk.z shl 4
        val chunkMaxZ = chunkMinZ + 15
        val originX = (chunkMinX - maxReadX)..(chunkMaxX - minReadX)
        val originZ = (chunkMinZ - maxReadZ)..(chunkMaxZ - minReadZ)
        return candidates.filterTo(HashSet()) { it.x in originX && it.z in originZ }
    }

    private fun List<CoarseEdge>.minimumCostsBy(node: (CoarseEdge) -> Stance): Map<Stance, Double> {
        val result = HashMap<Stance, Double>(size)
        for (edge in this) result.merge(node(edge), edge.lowerBoundTicks, ::minOf)
        return result
    }

    private fun Double.finiteOrZero() = if (isFinite()) this else 0.0

    companion object {
        fun build(costs: CoarseMoveCosts, options: SimpleMoveOptions = SimpleMoveOptions()): SimpleMoveLibrary {
            val specs = buildList {
                for ((dx, dz) in CARDINALS) {
                    add(Spec(dx, 0, dz, CoarseMoveKind.WALK, costs.cardinalWalk, stanceConditions(dx, 0, dz)))
                    if (options.allowStepUp) {
                        add(
                            Spec(
                                dx, 1, dz, CoarseMoveKind.STEP_UP, costs.stepUp,
                                stanceConditions(dx, 1, dz) + CellCondition(0, 2, 0, Condition.CENTER_SLICE),
                            )
                        )
                    }
                    for (depth in 1..options.maxWalkOffDepth) {
                        val corridor = (1 downTo 1 - depth).map { y -> CellCondition(dx, y, dz, Condition.CENTER_SLICE) }
                        add(
                            Spec(
                                dx, -depth, dz, CoarseMoveKind.WALK_OFF, costs.walkOffCost(depth),
                                stanceConditions(dx, -depth, dz) + corridor,
                            )
                        )
                    }
                    if (options.allowJumpCandidates) {
                        for (span in 2..options.maxJumpSpan) {
                            for (verticalOffset in -options.maxJumpDrop..1) {
                                add(
                                    jumpSpec(
                                        dx,
                                        dz,
                                        span,
                                        verticalOffset,
                                        costs.jumpCandidateCost(span, verticalOffset),
                                    )
                                )
                            }
                        }
                    }
                }
                if (options.allowDiagonal) {
                    for ((dx, dz) in DIAGONALS) {
                        add(
                            Spec(
                                dx, 0, dz, CoarseMoveKind.WALK, costs.diagonalWalk,
                                stanceConditions(dx, 0, dz) + listOf(
                                    CellCondition(dx, 0, 0, Condition.FULL_SLICE),
                                    CellCondition(dx, 1, 0, Condition.FULL_HEAD),
                                    CellCondition(0, 0, dz, Condition.FULL_SLICE),
                                    CellCondition(0, 1, dz, Condition.FULL_HEAD),
                                ),
                            )
                        )
                        if (options.allowJumpCandidates) {
                            for (span in 2..options.maxDiagonalJumpSpan) {
                                for (verticalOffset in -options.maxJumpDrop..1) {
                                    add(
                                        jumpSpec(
                                            dx, dz, span, verticalOffset,
                                            costs.diagonalJumpCandidateCost(span, verticalOffset),
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val templates = specs.mapIndexed { index, spec -> spec.toTemplate(MotionTemplateId(index)) }
            val offsets = templates.flatMapTo(HashSet()) { it.readOffsets().toList() }
            return SimpleMoveLibrary(templates, offsets, deriveCaps(templates))
        }

        private fun stanceConditions(dx: Int, dy: Int, dz: Int) = listOf(
            CellCondition(dx, dy - 1, dz, Condition.SUPPORT),
            CellCondition(dx, dy, dz, Condition.CENTER_SLICE),
            CellCondition(dx, dy + 1, dz, Condition.CENTER_HEAD),
        )

        private fun jumpSpec(dx: Int, dz: Int, span: Int, verticalOffset: Int, cost: Double): Spec =
            Spec(
                span * dx, verticalOffset, span * dz, CoarseMoveKind.JUMP_CANDIDATE, cost,
                stanceConditions(span * dx, verticalOffset, span * dz),
                arc = MotionTemplate.ArcSpec(dx, dz, span, verticalOffset),
            )

        private fun deriveCaps(templates: List<MotionTemplate>): HeuristicCaps {
            var axis = Double.POSITIVE_INFINITY
            var diagonal = Double.POSITIVE_INFINITY
            var ascent = Double.POSITIVE_INFINITY
            var descent = Double.POSITIVE_INFINITY
            for (template in templates) {
                val dx = abs(template.dx)
                val dz = abs(template.dz)
                when {
                    dx > 0 && dz == 0 -> axis = minOf(axis, template.lowerBoundTicks / dx)
                    dz > 0 && dx == 0 -> axis = minOf(axis, template.lowerBoundTicks / dz)
                    dx == dz && dx > 0 -> diagonal = minOf(diagonal, template.lowerBoundTicks / dx)
                }
                if (template.dy > 0) ascent = minOf(ascent, template.lowerBoundTicks / template.dy)
                if (template.dy < 0) descent = minOf(descent, template.lowerBoundTicks / abs(template.dy))
            }
            // Alternating opposing diagonals can make pure axial progress, while two
            // axial moves can make diagonal progress. Closing the caps under both
            // combinations keeps this octile lower bound consistent for every edge.
            axis = minOf(axis, diagonal)
            diagonal = minOf(diagonal, 2.0 * axis)
            return HeuristicCaps(axis, diagonal, ascent, descent)
        }

        private data class Spec(
            val dx: Int,
            val dy: Int,
            val dz: Int,
            val kind: CoarseMoveKind,
            val cost: Double,
            val conditions: List<CellCondition>,
            val arc: MotionTemplate.ArcSpec? = null,
        ) {
            fun toTemplate(id: MotionTemplateId) =
                MotionTemplate(id, dx, dy, dz, kind, cost, conditions, arc)
        }

        private val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)


    }
}
