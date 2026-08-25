package com.lambda.pathing.core

import net.minecraft.util.math.BlockPos

data class VoxelPos(val x: Int, val y: Int, val z: Int)

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
