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
import com.lambda.pathing.world.VoxelPos
import kotlin.math.abs
import kotlin.math.hypot

/** Immutable template set and every derived search property. */
class SimpleMoveLibrary private constructor(
    val templates: List<MotionTemplate>,
    private val readOffsets: Set<VoxelPos>,
    val heuristicCaps: HeuristicCaps,
) {
    data class HeuristicCaps(
        val horizontalTicksPerBlock: Double,
        val ascentTicksPerBlock: Double,
        val descentTicksPerBlock: Double,
    )

    fun isStance(view: CoarseVoxelView, stance: Stance): Boolean {
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
            if (isStance(view, origin) && template.target(origin) == target) template.edge(view, origin) else null
        }
    }

    fun successorCosts(view: CoarseVoxelView, origin: Stance): Map<Stance, Double> =
        edgesFrom(view, origin).minimumCostsBy { it.to }

    fun predecessorCosts(view: CoarseVoxelView, target: Stance): Map<Stance, Double> =
        edgesTo(view, target).minimumCostsBy { it.from }

    /** Max of independent axis lower bounds; summing would double-count diagonal/vertical moves. */
    fun heuristic(from: Stance, to: Stance): Double {
        val horizontalDistance = hypot((to.x - from.x).toDouble(), (to.z - from.z).toDouble())
        val horizontal = horizontalDistance * heuristicCaps.horizontalTicksPerBlock.finiteOrZero()
        val dy = to.y - from.y
        val vertical = when {
            dy > 0 -> dy * heuristicCaps.ascentTicksPerBlock.finiteOrZero()
            dy < 0 -> -dy * heuristicCaps.descentTicksPerBlock.finiteOrZero()
            else -> 0.0
        }
        return maxOf(horizontal, vertical)
    }

    /** Exact inverse of the derived template read offsets. */
    fun affectedOrigins(changed: VoxelPos): Set<Stance> = buildSet(readOffsets.size) {
        for (offset in readOffsets) {
            add(Stance(changed.x - offset.x, changed.y - offset.y, changed.z - offset.z))
        }
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
                        add(jumpSpec(dx, dz, rise = 0, costs.flatJumpCandidate))
                        add(jumpSpec(dx, dz, rise = 1, costs.risingJumpCandidate))
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

        private fun jumpSpec(dx: Int, dz: Int, rise: Int, cost: Double): Spec {
            val arc = listOf(
                CellCondition(0, 2, 0, Condition.CENTER_SLICE),
                CellCondition(dx, 0, dz, Condition.CENTER_SLICE),
                CellCondition(dx, 1, dz, Condition.CENTER_SLICE),
                CellCondition(dx, 2, dz, Condition.CENTER_SLICE),
            )
            return Spec(
                2 * dx, rise, 2 * dz, CoarseMoveKind.JUMP_CANDIDATE, cost,
                stanceConditions(2 * dx, rise, 2 * dz) + arc,
            )
        }

        private fun deriveCaps(templates: List<MotionTemplate>): HeuristicCaps {
            var horizontal = Double.POSITIVE_INFINITY
            var ascent = Double.POSITIVE_INFINITY
            var descent = Double.POSITIVE_INFINITY
            for (template in templates) {
                val horizontalDistance = hypot(template.dx.toDouble(), template.dz.toDouble())
                if (horizontalDistance > 0.0) horizontal = minOf(horizontal, template.lowerBoundTicks / horizontalDistance)
                if (template.dy > 0) ascent = minOf(ascent, template.lowerBoundTicks / template.dy)
                if (template.dy < 0) descent = minOf(descent, template.lowerBoundTicks / abs(template.dy))
            }
            return HeuristicCaps(horizontal, ascent, descent)
        }

        private data class Spec(
            val dx: Int,
            val dy: Int,
            val dz: Int,
            val kind: CoarseMoveKind,
            val cost: Double,
            val conditions: List<CellCondition>,
        ) {
            fun toTemplate(id: MotionTemplateId) =
                MotionTemplate(id, dx, dy, dz, kind, cost, conditions)
        }

        private val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    }
}
