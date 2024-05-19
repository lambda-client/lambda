package com.lambda.util.math

import com.lambda.util.math.MathUtils.sq
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.pow
import kotlin.math.roundToInt

object VecUtils {
    val Vec3d.blockPos: BlockPos
        get() = BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())

    infix fun Vec3d.dist(other: Vec3d) =
        this.distanceTo(other)

    infix fun Vec3d.distSq(other: Vec3d) =
        this.squaredDistanceTo(other)

    fun Vec3d.approximate(other: Vec3d, precision: Double = 2.0E-4) =
        (subtract(other) distSq Vec3d.ZERO) > precision.pow(2)

    infix fun Vec3i.distSq(other: Vec3d) =
        Vec3d.of(this) distSq other

    infix fun Vec3i.distSq(other: Vec3i) =
        (this.x - other.x).sq + (this.y - other.y).sq + (this.z - other.z).sq

    infix operator fun Vec3d.plus(other: Vec3d) =
        this.add(other)

    infix operator fun Vec3d.minus(other: Vec3d) =
        this.subtract(other)

    infix operator fun Vec3d.times(other: Vec3d) =
        this.multiply(other)

    infix operator fun Vec3d.div(other: Vec3d) =
        this.multiply(1.0 / other.x, 1.0 / other.y, 1.0 / other.z)

    infix operator fun Vec3d.times(other: Double) =
        this.multiply(other)

    infix operator fun Vec3d.div(other: Double) =
        this.multiply(1.0 / other)
}
