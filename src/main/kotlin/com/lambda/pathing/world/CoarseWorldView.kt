/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.world

import net.minecraft.util.shape.VoxelShape

data class VoxelPos(val x: Int, val y: Int, val z: Int)

/**
 * Small immutable trait vocabulary shared by snapshot capture and templates.
 * Cells we know nothing about are [UNKNOWN], never optimistic air.
 */
data class CoarseVoxel(
    val fullyPassable: Boolean,
    val centerPassable: Boolean,
    val standableFullTop: Boolean,
    /**
     * The cell's contents bulge into the cell above it -- a fence, a wall, a gate. The
     * cell above such a cell cannot be walked or flown through even when it is air.
     */
    val intrudesAbove: Boolean,
) {
    companion object {
        val AIR = CoarseVoxel(true, true, false, false)
        val FULL_BLOCK = CoarseVoxel(false, false, true, false)

        /**
         * We have no idea what is here -- outside the snapshot. Pessimistic on every
         * axis, including [intrudesAbove]: an uncaptured cell may well hold a fence.
         */
        val UNKNOWN = CoarseVoxel(false, false, false, true)

        /**
         * Lava, water, a ladder, a cobweb: we know *exactly* what is here, and the
         * trajectory layer refuses to model motion through it.
         *
         * That is not the same as knowing nothing, and collapsing the two costs real
         * topology. [UNKNOWN] claims to intrude into the cell above, so treating lava as
         * unknown made `CENTER_SLICE` reject every cell above it -- and therefore made a
         * jump *over* a lava pool unroutable, when clearing it is exactly what a jump is
         * for. None of these blocks bulge upward, so none of them should say they do.
         *
         * Impassable in itself, and never standable: the body may pass *above* this cell,
         * never *through* it. If an arc dips in anyway, the rollout still throws, so the
         * mask stays permissive (§5.1) while safety stays with simulation.
         */
        val HAZARD = CoarseVoxel(false, false, false, false)
    }
}

interface CoarseVoxelView {
    fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

    /**
     * The real captured collision shape of a cell, for masks that sweep the player's
     * box along an arc instead of reading boolean traits (the fidelity fix for
     * "the coarse jump goes through blocks"). Fail closed: outside the capture, or on
     * physics the simulator refuses to model, this is a full cube -- the trajectory
     * layer could never certify motion through such a cell anyway.
     *
     * Null means this view carries no shapes at all; shape-based masks must then
     * propose no edges rather than guess from traits.
     */
    fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

    /**
     * Stance heights the trajectory layer can fully simulate.
     *
     * The coarse mask may only propose stances the simulator can certify *every* move
     * from, and it cannot see that constraint itself: it inspects two cells above a
     * stance, while a sprint jump from that stance reads four. A view backed by a
     * bounded snapshot narrows this; an unbounded one leaves it open.
     *
     * @see com.lambda.util.player.prediction.SimulationSnapshotBounds.simulableStanceY
     */
    val simulableStanceY: IntRange get() = Int.MIN_VALUE..Int.MAX_VALUE
}
