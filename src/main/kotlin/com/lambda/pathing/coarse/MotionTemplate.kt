/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos

/**
 * One relative move a body can make, and the cell tests that admit it.
 *
 * A template knows nothing about which movement produced it beyond [movement], which is
 * how the graph stayed indifferent to the vocabulary growing: conditions are opaque
 * [CellCondition]s rather than cases of a shared enum, so a movement can test for water or
 * a ladder without every other movement learning about it.
 */
class MotionTemplate internal constructor(
    val id: MotionTemplateId,
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val movement: MovementId,
    val lowerBoundTicks: Double,
    private val conditions: List<CellCondition>,
    private val arc: ArcSpec? = null,
) {
    /**
     * A template whose edge is flown rather than walked.
     *
     * [modes] is what makes a drop a drop: restricting the solver to the non-jumping modes
     * is the difference between stepping off a ledge and leaping off it.
     */
    data class ArcSpec(
        val stepX: Int,
        val stepZ: Int,
        val span: Int,
        val rise: Int,
        val modes: List<LaunchMode> = LaunchMode.entries,
    )

    init {
        require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
        require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
            "Template lower bound must be finite and positive: $lowerBoundTicks"
        }
    }

    fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

    internal fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
        conditions.all { it.matches(view, origin.x, origin.y, origin.z) }

    internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
        if (!matches(view, origin)) return null
        val probed = arc?.let { spec ->
            JumpArcProbe.probe(
                view, origin, spec.stepX, spec.stepZ, spec.span, spec.rise, modes = spec.modes,
            ) ?: return null
        }
        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            movement = movement,
            lowerBoundTicks = lowerBoundTicks,
            readSet = buildSet {
                readOffsets().forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
                probed?.let { addAll(it.reads) }
            },
            launch = probed?.solution,
        )
    }

    /**
     * Every cell this template can read, relative to its origin.
     *
     * Incremental repair inverts this to find which stances a block change invalidates, so
     * anything read and not declared here becomes an edge nothing ever refreshes.
     */
    internal fun readOffsets(): Sequence<VoxelPos> = sequence {
        yieldAll(ORIGIN_STANCE_READS)
        conditions.forEach { yieldAll(it.reads()) }

        arc?.let { spec ->
            for (step in 0..spec.span) {
                for (y in minOf(spec.rise, 0) - 2..ARC_READ_CEILING) {
                    for (ox in -1..1) {
                        for (oz in -1..1) {
                            yield(VoxelPos(step * spec.stepX + ox, y, step * spec.stepZ + oz))
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val ORIGIN_STANCE_READS = listOf(
            VoxelPos(0, -1, 0),
            VoxelPos(0, 0, 0),
            VoxelPos(0, 1, 0),
        )

        const val ARC_READ_CEILING = 4
    }
}
