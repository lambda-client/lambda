package com.lambda.manager.interaction

import com.lambda.context.SafeContext
import net.minecraft.util.math.*
import java.util.*

object VisibilityChecker {
    operator fun DoubleArray.component6() = this[5]

    inline fun SafeContext.scanVisibleSurfaces(box: Box, resolution: Int, check: (Vec3d) -> Unit) {
        getVisibleSides(box).forEach { side ->
            val shrunk = box.expand(-0.025)
            val (minX, minY, minZ, maxX, maxY, maxZ) = shrunk.bounds(side)
            val stepX = (maxX - minX) / resolution
            val stepY = (maxY - minY) / resolution
            val stepZ = (maxZ - minZ) / resolution
            for (i in 0 .. resolution) {
                for (j in 0 .. resolution) {
                    val x = if (stepX != 0.0) minX + stepX * i else minX
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

    fun SafeContext.getVisibleSides(box: Box): List<Direction> {
        val visibleSides = EnumSet.noneOf(Direction::class.java)

        val eyePos = player.eyePos
        val center = box.center

        val halfX = box.lengthX / 2
        val halfY = box.lengthY / 2
        val halfZ = box.lengthZ / 2

        return visibleSides
            .checkAxis(eyePos.x - center.x, halfX, Direction.WEST, Direction.EAST)
            .checkAxis(eyePos.y - center.y, halfY, Direction.DOWN, Direction.UP)
            .checkAxis(eyePos.z - center.z, halfZ, Direction.NORTH, Direction.SOUTH)
            .map { it }
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
            else -> {}
        }
    }
}
