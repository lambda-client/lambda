package com.lambda.client.util.math

import com.lambda.client.commons.extension.PI_FLOAT
import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.floorToInt
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.*

object VectorUtils {

    /**
     * Get all block positions inside a 3d area between pos1 and pos2
     *
     * @param pos1 Starting blockPos
     * @param pos2 Ending blockPos
     * @return block positions inside a 3d area between pos1 and pos2
     */
    fun getBlockPositionsInArea(pos1: BlockPos, pos2: BlockPos): List<BlockPos> {
        val minX = min(pos1.x, pos2.x)
        val maxX = max(pos1.x, pos2.x)
        val minY = min(pos1.y, pos2.y)
        val maxY = max(pos1.y, pos2.y)
        val minZ = min(pos1.z, pos2.z)
        val maxZ = max(pos1.z, pos2.z)
        return getBlockPos(minX, maxX, minY, maxY, minZ, maxZ)
    }

    private fun getBlockPos(minX: Int, maxX: Int, minY: Int, maxY: Int, minZ: Int, maxZ: Int): List<BlockPos> {
        val returnList = ArrayList<BlockPos>()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    returnList.add(BlockPos(x, y, z))
                }
            }
        }
        return returnList
    }

    fun getBlockPosInSphere(center: Vec3d, radius: Float, xOffset: Double = 0.0, yOffset: Double = 0.0, zOffset: Double = 0.0) =
        getBlockPosInSphere(center, radius, Vec3d(xOffset, yOffset, zOffset))

    /**
     * @param center Center of the sphere
     * @param radius Radius of the sphere
     * @param offset Offset of the sphere
     * @return block positions inside a sphere with given [radius]
     */
    private fun getBlockPosInSphere(center: Vec3d, radius: Float, offset: Vec3d): ArrayList<BlockPos> {
        val squaredRadius = radius.pow(2)
        val posList = ArrayList<BlockPos>()
        for (x in getAxisRange(center.x, radius)) for (y in getAxisRange(center.y, radius)) for (z in getAxisRange(center.z, radius)) {
            /* Valid position check */
            val blockPos = BlockPos(x, y, z).add(offset.x, offset.y, offset.z)
            if (blockPos.distanceSqToCenter(center.x, center.y, center.z) > squaredRadius) continue
            posList.add(blockPos)
        }
        return posList
    }

    fun getBlocksInRange(center: Vec3d, minPos: Vec3d, maxPos: Vec3d): ArrayList<BlockPos> {
        val posList = ArrayList<BlockPos>()
        for (x in getAxisRangeIn(center.x, minPos.x, maxPos.x))
            for (y in getAxisRangeIn(center.y, minPos.y, maxPos.y))
                for (z in getAxisRangeIn(center.z, minPos.z, maxPos.z)) posList.add(BlockPos(x, y, z))
        return posList
    }


    private fun getAxisRange(d1: Double, d2: Float): IntRange {
        return IntRange((d1 - d2).floorToInt(), (d1 + d2).ceilToInt())
    }

    private fun getAxisRangeIn(center: Double, d1: Double, d2: Double): IntRange {
        return IntRange((center + d1).floorToInt(), (center + d2).ceilToInt())
    }

    fun Vec2f.toViewVec(): Vec3d {
        val rotationRad = toRadians()
        val yaw = -rotationRad.x - PI_FLOAT
        val pitch = -rotationRad.y

        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = -cos(pitch)
        val sinPitch = sin(pitch)

        return Vec3d((sinYaw * cosPitch).toDouble(), sinPitch.toDouble(), (cosYaw * cosPitch).toDouble())
    }

    /* For some reasons the mixin for the getVisibleFacings absolutely wanted that */
    fun toBlockPos(pos: Vec3d): BlockPos {
        return pos.toBlockPos()
    }

    fun Vec3d.toBlockPos(xOffset: Double = 0.0, yOffset: Double = 0.0, zOffset: Double = 0.0): BlockPos {
        return BlockPos(x.floorToInt() + xOffset, y.floorToInt() + yOffset, z.floorToInt() + zOffset)
    }

    fun Vec3i.toVec3d(): Vec3d {
        return toVec3d(0.0, 0.0, 0.0)
    }

    fun Vec3i.toVec3d(xOffset: Double, yOffset: Double, zOffset: Double): Vec3d {
        return Vec3d(x + xOffset, y + yOffset, z + zOffset)
    }

    fun Vec3i.toVec3dCenter(): Vec3d {
        return toVec3dCenter(0.0, 0.0, 0.0)
    }

    fun Vec3i.toVec3dCenter(xOffset: Double, yOffset: Double, zOffset: Double): Vec3d {
        return Vec3d(x + 0.5 + xOffset, y + 0.5 + yOffset, z + 0.5 + zOffset)
    }

    fun Vec3d.distanceTo(other: Vec3i): Double {
        return this.distanceTo(other)
    }

    fun Entity.distanceTo(pos: Vec3i): Double {
        return this.position.distanceTo(pos)
    }

    fun Entity.distanceTo(pos: Vec3d): Double {
        return this.position.distanceTo(pos.toBlockPos())
    }

    fun Vec3i.distanceTo(other: Vec3i): Double {
        return sqrt((other.x - x).toDouble().pow(2) + (other.y - y).toDouble().pow(2) + (other.z - z).toDouble().pow(2))
    }


    fun Entity.distanceTo(chunkPos: ChunkPos): Double {
        return hypot(chunkPos.x * 16 + 8 - posX, chunkPos.z * 16 + 8 - posZ)
    }

    infix operator fun Vec3d.times(vec3d: Vec3d): Vec3d = Vec3d(x * vec3d.x, y * vec3d.y, z * vec3d.z)

    infix operator fun Vec3d.times(multiplier: Double): Vec3d = Vec3d(x * multiplier, y * multiplier, z * multiplier)

    infix operator fun Vec3d.plus(vec3d: Vec3d): Vec3d = add(vec3d)

    infix operator fun Vec3d.minus(vec3d: Vec3d): Vec3d = subtract(vec3d)
}
