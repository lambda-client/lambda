/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.world

data class VoxelPos(val x: Int, val y: Int, val z: Int)

/**
 * Small immutable trait vocabulary shared by snapshot capture and templates.
 * Unknown or unsupported cells are [UNKNOWN], never optimistic air.
 */
data class CoarseVoxel(
    val fullyPassable: Boolean,
    val centerPassable: Boolean,
    val standableFullTop: Boolean,
    val intrudesAbove: Boolean,
) {
    companion object {
        val AIR = CoarseVoxel(true, true, false, false)
        val FULL_BLOCK = CoarseVoxel(false, false, true, false)
        val UNKNOWN = CoarseVoxel(false, false, false, true)
    }
}

interface CoarseVoxelView {
    fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

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
