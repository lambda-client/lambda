/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.movement.elytrafly

import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object GrimFireworkBox {
    fun computeFireworksBounds(
	    lastKnownClientVelocity: Vec3d,
	    currentRotation: Vec3d,
	    lastSentPitch: Float,
	    lastSentYaw: Float,
	    lastMovementIncludedPosition: Boolean,
	    rescaleAmount: Double,
	    gravity: Double
    ): DoubleArray? {
        val simulatedVelocity = calculateGlidingVelocity(lastKnownClientVelocity, currentRotation, gravity)

        val currentLook = currentRotation.normalize()
        val lastLook = Vec3d.fromPolar(lastSentPitch, lastSentYaw).normalize()
        val antiTickSkipping = if (lastMovementIncludedPosition) 0.05 else 0.0

        var minX = min(-antiTickSkipping, currentLook.x) + min(-antiTickSkipping, lastLook.x)
        var minY = min(-antiTickSkipping, currentLook.y) + min(-antiTickSkipping, lastLook.y)
        var minZ = min(-antiTickSkipping, currentLook.z) + min(-antiTickSkipping, lastLook.z)
        var maxX = max(antiTickSkipping, currentLook.x) + max(antiTickSkipping, lastLook.x)
        var maxY = max(antiTickSkipping, currentLook.y) + max(antiTickSkipping, lastLook.y)
        var maxZ = max(antiTickSkipping, currentLook.z) + max(antiTickSkipping, lastLook.z)

        if (rescaleAmount <= 0.0) return null

        minX = max(-rescaleAmount, minX * rescaleAmount)
        minY = max(-rescaleAmount, minY * rescaleAmount)
        minZ = max(-rescaleAmount, minZ * rescaleAmount)
        maxX = min(rescaleAmount, maxX * rescaleAmount)
        maxY = min(rescaleAmount, maxY * rescaleAmount)
        maxZ = min(rescaleAmount, maxZ * rescaleAmount)

        return doubleArrayOf(
            simulatedVelocity.x + min(0.0, minX - lastKnownClientVelocity.x),
            simulatedVelocity.y + min(0.0, minY - lastKnownClientVelocity.y),
            simulatedVelocity.z + min(0.0, minZ - lastKnownClientVelocity.z),
            simulatedVelocity.x + max(0.0, maxX - lastKnownClientVelocity.x),
            simulatedVelocity.y + max(0.0, maxY - lastKnownClientVelocity.y),
            simulatedVelocity.z + max(0.0, maxZ - lastKnownClientVelocity.z)
        )
    }

    fun calculateGlidingVelocity(oldVelocity: Vec3d, rotation: Vec3d, gravity: Double): Vec3d {
        val horizontalLookLength = rotation.horizontalLength()
        val horizontalSpeed = oldVelocity.horizontalLength()
        val pitch = asin(MathHelper.clamp(-rotation.y, -1.0, 1.0)).toFloat()
        val pitchCosSquared = MathHelper.square(cos(pitch.toDouble()))

        var velocity = oldVelocity.add(0.0, gravity * (pitchCosSquared * 0.75 - 1.0), 0.0)

        if (velocity.y < 0 && horizontalLookLength > 0) {
            val lift = velocity.y * -0.1 * pitchCosSquared
            velocity = velocity.add(rotation.x * lift / horizontalLookLength, lift, rotation.z * lift / horizontalLookLength)
        }

        if (pitch < 0 && horizontalLookLength > 0) {
            val dive = horizontalSpeed * -sin(pitch.toDouble()) * 0.04
            velocity = velocity.add(-rotation.x * dive / horizontalLookLength, dive * 3.2, -rotation.z * dive / horizontalLookLength)
        }

        if (horizontalLookLength > 0) {
            val targetScale = horizontalSpeed / horizontalLookLength
            velocity = velocity.add((rotation.x * targetScale - velocity.x) * 0.1, 0.0, (rotation.z * targetScale - velocity.z) * 0.1)
        }

        return velocity.multiply(0.99, 0.98, 0.99)
    }
}