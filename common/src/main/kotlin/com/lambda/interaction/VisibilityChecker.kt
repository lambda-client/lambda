package com.lambda.interaction

import com.lambda.context.SafeContext
import com.lambda.util.primitives.extension.component6
import net.minecraft.util.math.*
import java.util.*

object VisibilityChecker {
    inline fun SafeContext.scanVisibleSurfaces(box: Box, sides: Set<Direction>, resolution: Int, check: (Vec3d) -> Unit) {
        val shrunk = box.expand(-0.005)
        getVisibleSides(box)
            .forEach { side ->
                if (sides.isNotEmpty() && side !in sides) {
                    return@forEach
                }
                val (minX, minY, minZ, maxX, maxY, maxZ) = shrunk.bounds(side)
                val stepX = (maxX - minX) / resolution
                val stepY = (maxY - minY) / resolution
                val stepZ = (maxZ - minZ) / resolution
                for (i in 0 .. resolution) {
                    val x = if (stepX != 0.0) minX + stepX * i else minX
                    for (j in 0 .. resolution) {
                        val y = if (stepY != 0.0) minY + stepY * j else minY
                        val z = if (stepZ != 0.0) minZ + stepZ * ((if (stepX != 0.0) j else i)) else minZ
                        check(Vec3d(x, y, z))
                    }
                }
            }
    }

    fun Box.bounds(side: Direction) =
        when (side) {
            Direction.DOWN -> doubleArrayOf(minX, minY, minZ, maxX, minY, maxZ)
            Direction.UP -> doubleArrayOf(minX, maxY, minZ, maxX, maxY, maxZ)
            Direction.NORTH -> doubleArrayOf(minX, minY, minZ, maxX, maxY, minZ)
            Direction.SOUTH -> doubleArrayOf(minX, minY, maxZ, maxX, maxY, maxZ)
            Direction.WEST -> doubleArrayOf(minX, minY, minZ, minX, maxY, maxZ)
            Direction.EAST -> doubleArrayOf(maxX, minY, minZ, maxX, maxY, maxZ)
        }

    fun SafeContext.getVisibleSides(box: Box): Set<Direction> {
        val visibleSides = EnumSet.noneOf(Direction::class.java)

        val eyePos = player.eyePos
        val center = box.center

        return visibleSides
            .checkAxis(eyePos.x - center.x, box.lengthX / 2, Direction.WEST, Direction.EAST)
            .checkAxis(eyePos.y - center.y, box.lengthY / 2, Direction.DOWN, Direction.UP)
            .checkAxis(eyePos.z - center.z, box.lengthZ / 2, Direction.NORTH, Direction.SOUTH)
    }

    private fun EnumSet<Direction>.checkAxis(
        diff: Double,
        limit: Double,
        negativeSide: Direction,
        positiveSide: Direction
    ) = apply {
        when {
            diff < -limit -> {
                add(negativeSide)
            }
            diff > limit -> {
                add(positiveSide)
            }
        }
    }
}
