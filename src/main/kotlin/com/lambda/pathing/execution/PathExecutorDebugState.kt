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

/**
 * Lightweight snapshot exposed to HUD/render code.
 */
data class PathExecutorDebugState(
    val active: Boolean = false,
    val traversalId: Int? = null,
    val status: String = "Idle",
    val segmentIndex: Int = -1,
    val segmentCount: Int = 0,
    val segmentType: String? = null,
    val recoveryMode: RecoveryMode? = null,
    val remainingDistance: Double = 0.0,
    val segmentLength: Double = 0.0,
    val projectedDistance: Double = 0.0,
    val lateralError: Double = 0.0,
    val verticalError: Double = 0.0,
    val lostTicks: Int = 0,
    val skippedSegments: Int = 0,
    val rewoundSegments: Int = 0,
    val replansRequested: Int = 0,
    val lookaheadPoint: Vec3d? = null,
    val projectedPoint: Vec3d? = null,
    val segmentStart: Vec3d? = null,
    val segmentEnd: Vec3d? = null,
    val playerPosition: Vec3d? = null,
    val playerVelocity: Vec3d? = null,
    val moveDelta: Vec3d? = null,
    val desiredDelta: Vec3d? = null,
    val playerYaw: Double? = null,
    val desiredYaw: Double? = null,
    val movementBasisYaw: Double? = null,
    val effectiveMovementYaw: Double? = null,
    val throttle: Double = 1.0,
    val commandedForward: Double = 0.0,
    val commandedStrafe: Double = 0.0,
    val sprintCommand: Boolean = false,
    val jumpCommand: Boolean = false,
    /** Why the step-up jump command was or wasn't set this tick (planning side). */
    val jumpCommandGate: String = "",
    /** Why the jump input was or wasn't issued this tick (input side). */
    val jumpInputGate: String = "",
    val horizontalSpeed: Double = 0.0,
    val movedLastTick: Double = 0.0,
    val supportedSegment: Boolean = true,
) {
    val segmentProgressFraction: Double
        get() = if (segmentLength <= 1.0E-9) 0.0 else (projectedDistance / segmentLength).coerceIn(0.0, 1.0)
}

/**
 * Rolling per-tick sample used for more visual execution debugging.
 */
data class PathExecutorDebugSample(
    val status: String,
    val segmentIndex: Int,
    val segmentType: String?,
    val playerPosition: Vec3d,
    val projectedPoint: Vec3d?,
    val lookaheadPoint: Vec3d?,
    val playerVelocity: Vec3d,
    val moveDelta: Vec3d,
    val desiredDelta: Vec3d?,
    val lateralError: Double,
    val verticalError: Double,
    val remainingDistance: Double,
    val throttle: Double,
    val commandedForward: Double,
    val commandedStrafe: Double,
)
