/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.VoxelPos

/**
 * A test a movement makes on one cell relative to its origin.
 *
 * This replaced a five-value enum, which is why walking was the only thing the graph
 * could express: there was no way to say "this cell is water" or "this cell is a ladder"
 * without adding a case to a type every movement shared.
 *
 * [extraReads] is not optional bookkeeping. Incremental repair works out which stances a
 * block change can affect by unioning every template's read offsets, so a predicate that
 * quietly reads a cell it did not declare produces a graph that stops updating when that
 * cell changes -- a stale edge that nothing ever invalidates.
 */
fun interface CellPredicate {
    fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean

    /** Cells this predicate reads beyond the one it is anchored at, as relative offsets. */
    val extraReads: List<VoxelPos> get() = emptyList()

    companion object {
        /**
         * Something the body can stand on, with nothing poking up out of it.
         *
         * Deliberately does not care *how high* the surface is -- that is the caller's
         * business, and asking here would make every template's conditions depend on a
         * height they cannot see. A cell holds a body up or it does not.
         *
         * Reads the cell below as well, because a surface is not always in the cell that
         * owns it: a fence is taller than its own cell, so the standing it provides happens
         * in the neighbour above. Asking one cell found air there and no fence line was ever
         * walkable.
         */
        val SUPPORT = below { view, x, y, z ->
            view.standingSurface(x, y, z) != null && !view.voxel(x, y, z).intrudesAbove
        }

        /** The body's centre column is clear, and the cell below does not intrude into it. */
        val CENTER_SLICE = below { view, x, y, z ->
            view.voxel(x, y, z).centerPassable && !view.voxel(x, y - 1, z).intrudesAbove
        }

        /** The whole cell is clear, and the cell below does not intrude into it. */
        val FULL_SLICE = below { view, x, y, z ->
            view.voxel(x, y, z).fullyPassable && !view.voxel(x, y - 1, z).intrudesAbove
        }

        val CENTER_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).centerPassable }

        val FULL_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

        /** The cell is exactly this medium. */
        fun mediumIs(medium: Medium) = CellPredicate { view, x, y, z -> view.medium(x, y, z) == medium }

        /** Something a body can hold onto: a ladder, a vine. */
        val CLIMBABLE = mediumIs(Medium.CLIMBABLE)

        /** A predicate that also reads the cell directly beneath its anchor. */
        private fun below(test: CellPredicate) = object : CellPredicate {
            override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int) = test.matches(view, x, y, z)
            override val extraReads = listOf(VoxelPos(0, -1, 0))
        }
    }
}

/** One [CellPredicate] bound to an offset from the movement's origin stance. */
data class CellCondition(val offset: VoxelPos, val predicate: CellPredicate) {
    constructor(dx: Int, dy: Int, dz: Int, predicate: CellPredicate) :
        this(VoxelPos(dx, dy, dz), predicate)

    fun matches(view: CoarseVoxelView, originX: Int, originY: Int, originZ: Int): Boolean =
        predicate.matches(view, originX + offset.x, originY + offset.y, originZ + offset.z)

    /** Every cell this condition touches, as offsets from the origin. */
    fun reads(): List<VoxelPos> = buildList {
        add(offset)
        predicate.extraReads.forEach {
            add(VoxelPos(offset.x + it.x, offset.y + it.y, offset.z + it.z))
        }
    }
}
