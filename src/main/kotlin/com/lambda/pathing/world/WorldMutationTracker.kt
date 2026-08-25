/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.world

import net.minecraft.util.math.BlockPos

data class PathingSection(val x: Int, val y: Int, val z: Int) {
    companion object {
        fun containing(pos: VoxelPos) = PathingSection(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        fun containing(pos: BlockPos) = PathingSection(pos.x shr 4, pos.y shr 4, pos.z shr 4)
    }
}

data class PathingChunk(val x: Int, val z: Int) {
    companion object {
        fun containing(pos: VoxelPos) = PathingChunk(pos.x shr 4, pos.z shr 4)
    }
}

sealed interface WorldMutation {
    val revision: Long

    data class Section(
        val section: PathingSection,
        override val revision: Long,
    ) : WorldMutation

    data class Chunk(
        val chunk: PathingChunk,
        override val revision: Long,
    ) : WorldMutation
}

