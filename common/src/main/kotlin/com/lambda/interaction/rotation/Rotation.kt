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

package com.lambda.interaction.rotation

import com.lambda.Lambda.mc
import com.lambda.threading.runSafe
import com.lambda.util.math.MathUtils.toDegree
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.world.raycast.RayCastMask
import com.lambda.util.world.raycast.RayCastUtils.rayCast
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.*

data class Rotation(val yaw: Double, val pitch: Double) {
    constructor(yaw: Float, pitch: Float) : this(yaw.toDouble(), pitch.toDouble())

    val yawF get() = yaw.toFloat()
    val pitchF get() = pitch.toFloat()
    val float get() = floatArrayOf(yawF, pitchF)

    fun equalFloat(other: Rotation): Boolean = yawF == other.yawF && pitchF == other.pitchF

    val vector: Vec3d
        get() {
            val yawRad = -yaw.toRadian()
            val pitchRad = pitch.toRadian()

            return Vec3d(sin(yawRad), -1.0, cos(yawRad))
                .multiply(Vec3d(cos(pitchRad), sin(pitchRad), cos(pitchRad)))
        }

    fun withDelta(yaw: Double = 0.0, pitch: Double = 0.0) =
        Rotation(this.yaw + yaw, (this.pitch + pitch).coerceIn(-90.0, 90.0))

    fun rayCast(
        reach: Double,
        eye: Vec3d? = null,
        fluids: Boolean = false,
        mask: RayCastMask = RayCastMask.BOTH,
    ) = runSafe {
        rayCast(eye ?: player.eyePos, vector, reach, mask, fluids)
    }

    fun castBox(
        box: Box,
        reach: Double,
        eye: Vec3d? = null,
    ) = runSafe {
        val eyeVec = eye ?: player.eyePos
        box.raycast(eyeVec, eyeVec + vector * reach).orElse(null)
    }

    companion object {
        val Direction.yaw: Float
            get() = when (this) {
                Direction.NORTH -> -180.0f
                Direction.SOUTH -> 0.0f
                Direction.EAST -> -90.0f
                Direction.WEST -> 90.0f
                else -> 0.0f
            }

        val ZERO = Rotation(0.0, 0.0)
        val DOWN = Rotation(0.0, 90.0)
        val UP = Rotation(0.0, -90.0)
        val Direction.rotation get() = Rotation(yaw.toDouble(), 0.0)
        var Entity.rotation
            get() = Rotation(yaw, pitch)
            set(value) {
                yaw = value.yawF
                pitch = value.pitchF
            }

        fun wrap(deg: Double) = MathHelper.wrapDegrees(deg)

        fun Rotation.lerp(other: Rotation, delta: Double): Rotation {
            val yaw = this.yaw + delta * (other.yaw - this.yaw)
            val pitch = this.pitch + delta * (other.pitch - this.pitch)
            return Rotation(yaw, pitch)
        }

        fun Rotation.slerp(other: Rotation, speed: Double): Rotation {
            val yawDiff = wrap(other.yaw - yaw)
            val pitchDiff = wrap(other.pitch - pitch)

            val diff = hypot(yawDiff, pitchDiff)

            val yawSpeed = abs(yawDiff / diff) * speed
            val pitchSpeed = abs(pitchDiff / diff) * speed

            val yaw = yaw + yawDiff.coerceIn(-yawSpeed, yawSpeed)
            val pitch = pitch + pitchDiff.coerceIn(-pitchSpeed, pitchSpeed)

            return Rotation(yaw, pitch)
        }

        fun Rotation.fixSensitivity(prev: Rotation): Rotation {
            val f = mc.options.mouseSensitivity.value * 0.6 + 0.2
            val gcd = f * f * f * 8.0 * 0.15F

            val r1 = Vec2d(prev.yaw, prev.pitch)
            val r2 = Vec2d(this.yaw, this.pitch)
            val delta = ((r2 - r1) / gcd).roundToInt() * gcd
            val fixed = r1 + delta

            return Rotation(fixed.x, fixed.y.coerceIn(-90.0, 90.0))
        }

        fun Vec3d.rotationTo(vec: Vec3d): Rotation {
            val diffX = vec.x - x
            val diffY = vec.y - y
            val diffZ = vec.z - z

            val yawRad = atan2(diffZ, diffX)
            val pitchRad = -atan2(diffY, hypot(diffX, diffZ))

            val yaw = wrap(yawRad.toDegree() - 90.0)
            val pitch = wrap(pitchRad.toDegree())

            return Rotation(yaw, pitch)
        }

        infix fun Rotation.dist(b: Rotation) =
            hypot(
                wrap(yaw - b.yaw),
                wrap(pitch - b.pitch)
            )

        fun angleDifference(a: Double, b: Double) =
            abs(wrap(a - b))
    }
}
