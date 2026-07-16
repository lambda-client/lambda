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
import com.lambda.pathing.world.VoxelPos

/**
 * A forward motion primitive expressed entirely as data. Predecessors stamp
 * the same template at `target - displacement`; they never assume the move is
 * reversible. This is essential for walk-off drops and later one-way moves.
 */
class MotionTemplate internal constructor(
    val id: MotionTemplateId,
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val kind: CoarseMoveKind,
    val lowerBoundTicks: Double,
    private val conditions: List<CellCondition>,
    private val arc: ArcSpec? = null,
) {
    internal data class CellCondition(
        val dx: Int,
        val dy: Int,
        val dz: Int,
        val condition: Condition,
    )

    /** A swept-arc mask requirement: the probe must clear it against real shapes. */
    internal data class ArcSpec(
        val stepX: Int,
        val stepZ: Int,
        val span: Int,
        val rise: Int,
    )

    internal enum class Condition {
        SUPPORT,
        CENTER_SLICE,
        FULL_SLICE,
        CENTER_HEAD,
        FULL_HEAD,
    }

    init {
        require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
        require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
            "Template lower bound must be finite and positive: $lowerBoundTicks"
        }
    }

    fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

    internal fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
        conditions.all { it.matches(view, origin) }

    internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
        if (!matches(view, origin)) return null
        val probed = arc?.let { spec ->
            JumpArcProbe.probe(view, origin, spec.stepX, spec.stepZ, spec.span, spec.rise) ?: return null
        }
        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            kind = kind,
            lowerBoundTicks = lowerBoundTicks,
            readSet = buildSet {
                addAll(ORIGIN_STANCE_READS.map { it.absolute(origin) })
                conditions.forEach { condition ->
                    add(VoxelPos(origin.x + condition.dx, origin.y + condition.dy, origin.z + condition.dz))
                    if (condition.condition == Condition.CENTER_SLICE || condition.condition == Condition.FULL_SLICE) {
                        add(VoxelPos(origin.x + condition.dx, origin.y + condition.dy - 1, origin.z + condition.dz))
                    }
                }
                probed?.let { addAll(it.reads) }
            },
            jumpHint = probed?.hint,
        )
    }

    internal fun readOffsets(): Sequence<VoxelPos> = sequence {
        yieldAll(ORIGIN_STANCE_READS)
        for (condition in conditions) {
            yield(VoxelPos(condition.dx, condition.dy, condition.dz))
            if (condition.condition == Condition.CENTER_SLICE || condition.condition == Condition.FULL_SLICE) {
                yield(VoxelPos(condition.dx, condition.dy - 1, condition.dz))
            }
        }
        // Static superset of every cell an arc sweep may consult (the probe's exact
        // reads are runtime data); over-approximating only over-invalidates, safely.
        arc?.let { spec ->
            // The clearance margin reads one cell beyond the swept core on every side.
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

    private fun CellCondition.matches(view: CoarseVoxelView, origin: Stance): Boolean {
        val voxel = view.voxel(origin.x + dx, origin.y + dy, origin.z + dz)
        return when (condition) {
            Condition.SUPPORT -> voxel.standableFullTop && !voxel.intrudesAbove
            Condition.CENTER_SLICE -> voxel.centerPassable &&
                !view.voxel(origin.x + dx, origin.y + dy - 1, origin.z + dz).intrudesAbove
            Condition.FULL_SLICE -> voxel.fullyPassable &&
                !view.voxel(origin.x + dx, origin.y + dy - 1, origin.z + dz).intrudesAbove
            Condition.CENTER_HEAD -> voxel.centerPassable
            Condition.FULL_HEAD -> voxel.fullyPassable
        }
    }

    private fun VoxelPos.absolute(origin: Stance) = VoxelPos(origin.x + x, origin.y + y, origin.z + z)

    private companion object {
        val ORIGIN_STANCE_READS = listOf(
            VoxelPos(0, -1, 0),
            VoxelPos(0, 0, 0),
            VoxelPos(0, 1, 0),
        )

        /** Apex feet (~1.26) + body height + clearance margin, in whole cells above takeoff. */
        const val ARC_READ_CEILING = 4
    }
}
