package com.lambda.graphics.renderer.esp

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object DirectionMask {
    const val EAST  = 1 // X +
    const val WEST  = 2 // X -

    const val UP    = 4 // Y +
    const val DOWN  = 8 // Y -

    const val SOUTH = 16 // Z +
    const val NORTH = 32 // Z -

    const val ALL = EAST or WEST or UP or DOWN or SOUTH or NORTH
    const val NONE = 0

    fun Int.include(dir: Int) = this or dir
    fun Int.include(direction: Direction) = include(direction.mask)
    fun Int.exclude(dir: Int) = this xor dir
    fun Int.exclude(direction: Direction) = exclude(direction.mask)
    fun Int.hasDirection(dir: Int) = (this and dir) != 0

    fun buildSideMesh(blockPos: BlockPos, filter: (BlockPos) -> Boolean): Int {
        var sides = ALL

        Direction.entries
            .filter { filter(blockPos.offset(it)) }
            .forEach { sides = sides.exclude(it.mask) }

        return sides
    }

    val Direction.mask get() = when (this) {
        Direction.DOWN -> DOWN
        Direction.UP -> UP
        Direction.NORTH -> NORTH
        Direction.SOUTH -> SOUTH
        Direction.WEST -> WEST
        Direction.EAST -> EAST
    }

    enum class OutlineMode(val check: (Boolean, Boolean) -> Boolean) {
        // Render engine will add a line if BOTH touching sides are included into the mask
        AND(Boolean::and),

        // Render engine will add a line if ANY OF touching sides is included into the mask
        OR(Boolean::or)
    }
}