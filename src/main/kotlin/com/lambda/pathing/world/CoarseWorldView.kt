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
import net.minecraft.util.shape.VoxelShapes

data class VoxelPos(val x: Int, val y: Int, val z: Int)

/**
 * What a cell's collision amounts to, for callers that mostly do not need the shape.
 *
 * The arc sweep asks tens of thousands of cells per expanded node and almost every answer
 * is "nothing here" or "a plain cube" -- both decidable without a [VoxelShape] in hand,
 * and `VoxelShape.getBoundingBoxes` allocates a fresh list on every call. Classifying once
 * lets the sweep do inline arithmetic against the unit cube and fetch real shapes only for
 * the [PARTIAL] minority: slabs, fences, stairs.
 */
enum class CollisionClass {
    /** No collision anywhere in the cell. */
    EMPTY,

    /** Exactly the unit cube; obstacle math needs no shape object. */
    FULL,

    /** Some other shape; the exact [VoxelShape] must be consulted. */
    PARTIAL,

    /** Uncaptured terrain; a sweep through it must fail closed. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Classifies a shape, conservatively: a full cube that goes unrecognized is merely
         * [PARTIAL] and takes the exact path, never the other way around.
         */
        fun of(shape: VoxelShape): CollisionClass = when {
            shape.isEmpty -> EMPTY
            shape === VoxelShapes.fullCube() -> FULL
            isFullCube(shape) -> FULL
            else -> PARTIAL
        }

        /** Bounds test instead of `Block.isShapeFullCube`, whose class init needs registries. */
        private fun isFullCube(shape: VoxelShape): Boolean {
            val boxes = shape.boundingBoxes
            if (boxes.size != 1) return false
            val box = boxes[0]
            return box.minX <= EPSILON && box.minY <= EPSILON && box.minZ <= EPSILON &&
                box.maxX >= 1.0 - EPSILON && box.maxY >= 1.0 - EPSILON && box.maxZ >= 1.0 - EPSILON
        }

        private const val EPSILON = 1.0E-7
    }
}

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
    /**
     * Where a body standing in this cell rests, as a height within the cell, or null if
     * nothing here holds one up.
     *
     * A height rather than a flag, and that is the whole of what made non-cube terrain
     * invisible to the planner. The old question was "is the top face a full solid square",
     * which a slab, a snow layer, a dirt path and a stairs block all answer no to -- so
     * none of them carried a stance, and a cell that is neither standable nor passable is a
     * wall. The simulator would have walked every one of them without noticing.
     *
     * Measured over the body's centred column rather than the whole cell, matching what
     * [centerPassable] already assumes: a stairs block's raised half is under the body's
     * middle, so the body rests on it at 1.0 rather than on the tread at 0.5.
     *
     * A shape that reaches above its own cell -- a fence post, a wall -- provides no
     * surface *here*: the body it holds up is standing in the cell above, part way up it.
     * [intrusionHeight] is what that cell needs to know about this one.
     */
    val standingSurface: Double?,
    /**
     * How far this cell's shape reaches into the cell above, or zero.
     *
     * A fence is 1.5 blocks tall, so it pokes half a block into its neighbour and a body on
     * top of it stands at 0.5 within that neighbour -- not on the fence's own cell at all.
     * Without this the cell above a fence is plain air holding nothing up, which is why the
     * planner would not walk a fence line: the stance the body actually occupies had no
     * support under it.
     */
    val intrusionHeight: Double = 0.0,
    /**
     * Whether landing here reflects the fall instead of stopping it.
     *
     * A geometric trait rather than a [Medium], because slime is solid ground in every way
     * that matters to a walking body -- it is standable, it blocks, it is not a hazard. What
     * makes it interesting is what it does to a *landing*, which is a property of the surface
     * and not of what the cell is made of.
     */
    val bouncy: Boolean = false,
    val medium: Medium = if (fullyPassable) Medium.AIR else Medium.SOLID,
) {
    /** How far below the cell's own top the feet sit; 0.0 for a full block. */
    val surfaceOffset: Double get() = (standingSurface ?: 1.0) - 1.0

    val standable: Boolean get() = standingSurface != null

    val intrudesAbove: Boolean get() = intrusionHeight > 0.0

    companion object {
        /** Unstreamed terrain is assumed to fill the cell above it, as it always was. */
        const val UNKNOWN_INTRUSION = 1.0

        val AIR = CoarseVoxel(true, true, null, 0.0, false, Medium.AIR)
        val FULL_BLOCK = CoarseVoxel(false, false, 1.0, 0.0, false, Medium.SOLID)
        val UNKNOWN = CoarseVoxel(false, false, null, UNKNOWN_INTRUSION, false, Medium.UNKNOWN)
        val HAZARD = CoarseVoxel(false, false, null, 0.0, false, Medium.UNKNOWN)

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
            standingSurface = null,
            medium = medium,
        )
    }
}

interface CoarseVoxelView {
    fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

    fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

    /**
     * The cheap classification of [collisionShape]; overridden where the answer is known
     * without building a shape. Must agree with [collisionShape]: a null shape is
     * [CollisionClass.UNKNOWN], and a class of [CollisionClass.PARTIAL] promises the shape
     * is available.
     */
    fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
        val shape = collisionShape(x, y, z) ?: return CollisionClass.UNKNOWN
        return CollisionClass.of(shape)
    }

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

    /**
     * How high a body standing in this cell rests, or null if nothing holds one up here.
     *
     * Two cells, because a standing surface is not always in the cell that owns it. The
     * cell's own shape answers for anything that fits inside it -- a slab, a snow layer, a
     * whole block. A fence or a wall is taller than its cell, so the surface it provides
     * lands in the cell *above*, and asking only the cell's own shape finds air there.
     *
     * The cell's own shape wins when both answer: a slab sitting on top of a fence post is
     * what the body stands on, not the post inside it.
     */
    fun standingSurface(x: Int, y: Int, z: Int): Double? {
        voxel(x, y, z).standingSurface?.let { return it }
        return voxel(x, y - 1, z).intrusionHeight.takeIf { it > 0.0 && it < 1.0 }
    }

    /** Where the feet of a body standing in this cell sit, relative to the cell's top. */
    fun surfaceOffset(x: Int, y: Int, z: Int): Double = (standingSurface(x, y, z) ?: 1.0) - 1.0
}
