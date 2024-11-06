/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util.math

import com.lambda.util.math.MathUtils.sq
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.pow
import kotlin.math.roundToInt

object VecUtils {
    val Vec3d.blockPos: BlockPos
        get() = BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())

    val Vec3i.vec3d get() = Vec3d.of(this)

    fun BlockPos.getHitVec(side: Direction) =
        vec3d + side.hitVecOffset

    val Direction.hitVecOffset
        get() =
            CENTER + vector.vec3d * 0.5

    infix fun Vec3d.dist(other: Vec3d): Double = this.distanceTo(other)

    infix fun Vec3d.distSq(other: Vec3d): Double = this.squaredDistanceTo(other)

    fun Vec3d.approximate(other: Vec3d, precision: Double = 2.0E-4): Boolean =
        (subtract(other) distSq Vec3d.ZERO) > precision.pow(2)

    infix fun Vec3i.distSq(other: Vec3d): Double = Vec3d.of(this) distSq other

    infix fun Vec3i.distSq(other: Vec3i): Int = (this.x - other.x).sq + (this.y - other.y).sq + (this.z - other.z).sq

    infix fun Entity.distSq(other: Vec3d): Double = this.pos distSq other

    infix fun Entity.distSq(other: Vec3i): Int = this.blockPos distSq other

    infix operator fun Vec3d.plus(other: Vec3d): Vec3d = this.add(other)

    infix operator fun Vec3d.minus(other: Vec3d): Vec3d = this.subtract(other)

    infix operator fun Vec3d.times(other: Vec3d): Vec3d = this.multiply(other)

    infix operator fun Vec3d.div(other: Vec3d): Vec3d = this.multiply(1.0 / other.x, 1.0 / other.y, 1.0 / other.z)

    infix operator fun Vec3d.times(other: Double): Vec3d = this.multiply(other)

    infix operator fun Vec3d.div(other: Double): Vec3d = this.multiply(1.0 / other)

    val UP = Vec3d(0.0, 1.0, 0.0)
    val DOWN = Vec3d(0.0, -1.0, 0.0)
    val CENTER = Vec3d(0.5, 0.5, 0.5)
}
