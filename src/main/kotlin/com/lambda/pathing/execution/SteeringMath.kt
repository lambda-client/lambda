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

package com.lambda.pathing.execution

import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Local movement input represented in the basis of some world-space yaw.
 */
data class SteeringInput(
    val forward: Double,
    val strafe: Double,
) {
    companion object {
        val ZERO = SteeringInput(0.0, 0.0)
    }
}

/**
 * Projects a desired horizontal world-space delta into forward/strafe movement
 * components for an actor whose movement basis is aligned with [basisYaw].
 */
fun projectWorldDeltaToLocalInput(worldDelta: Vec3d, basisYaw: Double): SteeringInput {
    if (worldDelta.lengthSquared() <= 1.0E-9) return SteeringInput.ZERO

    val yawRad = Math.toRadians(basisYaw)
    val forwardAxisX = -sin(yawRad)
    val forwardAxisZ = cos(yawRad)
    val rightAxisX = cos(yawRad)
    val rightAxisZ = sin(yawRad)

    var forward = worldDelta.x * forwardAxisX + worldDelta.z * forwardAxisZ
    var strafe = worldDelta.x * rightAxisX + worldDelta.z * rightAxisZ
    val magnitude = hypot(forward, strafe)
    if (magnitude > 1.0) {
        forward /= magnitude
        strafe /= magnitude
    }

    return SteeringInput(forward, strafe)
}
