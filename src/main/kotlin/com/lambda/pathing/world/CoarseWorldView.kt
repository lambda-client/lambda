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

        val HAZARD = CoarseVoxel(false, false, false, false)
    }
}

interface CoarseVoxelView {
    fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

    fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

    /**
     * False where the client has not streamed this cell yet.
     *
     * Unstreamed terrain is not modelled as terrain -- [voxel] fails closed there, so
     * it carries no stances and no edges. What crosses it is a single optimistic edge
     * from the reachable frontier to the goal, which the graph retires as the real
     * chunks arrive.
     */
    fun isKnown(x: Int, y: Int, z: Int): Boolean = true

    val simulableStanceY: IntRange get() = Int.MIN_VALUE..Int.MAX_VALUE
}
