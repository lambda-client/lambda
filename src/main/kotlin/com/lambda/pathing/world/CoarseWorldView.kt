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
 * What a cell *is*, as opposed to what shape it has.
 *
 * The geometric traits below answer "can a walking body pass through here", which is the
 * only question the planner used to ask. A body that swims, dives, climbs or rides asks a
 * different one, and the answer is not derivable from collision shapes: water and air are
 * both empty, and a ladder is empty and climbable at once.
 *
 * [UNKNOWN] is the honest answer for terrain the client has not streamed, and for media no
 * registered movement claims. Both fail closed.
 */
enum class Medium {
    AIR,
    WATER,
    LAVA,
    POWDER_SNOW,
    COBWEB,
    CLIMBABLE,
    SOLID,
    UNKNOWN,
    ;

    /** Whether a body can be inside this cell at all without a movement that handles it. */
    val breathable: Boolean get() = this == AIR
}

data class CoarseVoxel(
    val fullyPassable: Boolean,
    val centerPassable: Boolean,
    val standableFullTop: Boolean,
    val intrudesAbove: Boolean,
    val medium: Medium = if (fullyPassable) Medium.AIR else Medium.SOLID,
) {
    companion object {
        val AIR = CoarseVoxel(true, true, false, false, Medium.AIR)
        val FULL_BLOCK = CoarseVoxel(false, false, true, false, Medium.SOLID)
        val UNKNOWN = CoarseVoxel(false, false, false, true, Medium.UNKNOWN)
        val HAZARD = CoarseVoxel(false, false, false, false, Medium.UNKNOWN)

        /**
         * A cell a walking body cannot use, but another movement might.
         *
         * Distinct from [UNKNOWN]: the client knows exactly what is here, so a movement
         * that understands the medium may claim it. It stays impassable to everything
         * else, which is what keeps a walking route out of a lake.
         */
        fun of(medium: Medium, passable: Boolean = true) = CoarseVoxel(
            fullyPassable = passable,
            centerPassable = passable,
            standableFullTop = false,
            intrudesAbove = false,
            medium = medium,
        )
    }
}

interface CoarseVoxelView {
    fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

    fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

    fun medium(x: Int, y: Int, z: Int): Medium = voxel(x, y, z).medium

    /**
     * False where the client has not streamed this cell yet.
     *
     * Unstreamed terrain is not modelled as terrain -- [voxel] fails closed there, so it
     * carries no stances and no edges. What crosses it is a single optimistic edge from
     * the reachable frontier to the goal, which the graph retires as the real chunks
     * arrive.
     */
    fun isKnown(x: Int, y: Int, z: Int): Boolean = true

    val simulableStanceY: IntRange get() = Int.MIN_VALUE..Int.MAX_VALUE
}
