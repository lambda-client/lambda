package com.lambda.interaction.rotation

import com.lambda.Lambda.mc
import com.lambda.threading.runSafe
import com.lambda.util.math.MathUtils.toDegree
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.world.raycast.RayCastMask
import com.lambda.util.world.raycast.RayCastUtils.rayCast
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.*

data class Rotation(val yaw: Double, val pitch: Double) {
    constructor(yaw: Float, pitch: Float) : this(yaw.toDouble(), pitch.toDouble())

    val vector: Vec3d get() {
        val yawRad = -yaw.toRadian()
        val pitchRad = pitch.toRadian()

        return Vec3d(sin(yawRad), -1.0, cos(yawRad))
            .multiply(Vec3d(cos(pitchRad), sin(pitchRad), cos(pitchRad)))
    }

    fun withDelta(yaw: Double = 0.0, pitch: Double = 0.0) =
        Rotation(this.yaw + yaw, (this.pitch + pitch).coerceIn(-90.0, 90.0))

    fun rayCast(
        reach: Double,
        mask: RayCastMask = RayCastMask.BOTH,
        eye: Vec3d? = null,
        fluids: Boolean = false,
    ) = runSafe {
        rayCast(eye ?: player.eyePos, vector, reach, mask, fluids)
    }

    val Direction.yaw: Float
        get() = when (this) {
            Direction.NORTH -> -180.0f
            Direction.SOUTH -> 0.0f
            Direction.EAST -> -90.0f
            Direction.WEST -> 90.0f
            else -> 0.0f
        }

    companion object {
        val ZERO = Rotation(0.0, 0.0)
        val DOWN = Rotation(0.0, 90.0)

        private fun wrap(deg: Double) = MathHelper.wrapDegrees(deg)

        fun Rotation.interpolate(other: Rotation, delta: Double): Rotation {
            val yawDiff = wrap(other.yaw - yaw)
            val pitchDiff = wrap(other.pitch - pitch)

            val diff = hypot(yawDiff, pitchDiff)

            val yawSpeed = abs(yawDiff / diff) * delta
            val pitchSpeed = abs(pitchDiff / diff) * delta

            val yaw = yaw + yawDiff.coerceIn(-yawSpeed, yawSpeed)
            val pitch = pitch + pitchDiff.coerceIn(-pitchSpeed, pitchSpeed)

            return Rotation(yaw, pitch)
        }

        fun Rotation.fixSensitivity(last: Rotation): Rotation {
            val f = mc.options.mouseSensitivity.value * 0.6 + 0.2
            val step = f * f * f * 8.0 * 0.15F

            val deltaYaw = yaw - last.yaw
            var fixedYaw = (deltaYaw / step).roundToInt() * step
            fixedYaw += last.yaw

            val deltaPitch = pitch - last.pitch
            var fixedPitch = (deltaPitch / step).roundToInt() * step
            fixedPitch += last.pitch
            fixedPitch = fixedPitch.coerceIn(-90.0, 90.0)

            return Rotation(fixedYaw, fixedPitch)
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

        fun Rotation.distance(b: Rotation) =
            hypot(
                wrap(yaw - b.yaw),
                wrap(pitch - b.pitch)
            )

        fun angleDifference(a: Double, b: Double) =
            abs(wrap(a - b))
    }
}