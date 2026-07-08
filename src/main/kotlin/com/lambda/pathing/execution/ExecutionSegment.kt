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

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import net.minecraft.util.math.Vec3d

sealed interface ExecutionSegment {
    val index: Int
    val typeName: String
    val startPose: ExecutionPose
    val endPose: ExecutionPose
    val safeToCancel: Boolean
    val supportedByController: Boolean
    val requiresVerticalMotion: Boolean
    val horizontalLength: Double

    /**
     * Signed projected progress in blocks along the segment measured in the xz
     * plane. Negative values mean the player is still behind the start.
     */
    fun projectedDistance(position: Vec3d): Double

    /** Horizontal drift away from the segment corridor in blocks. */
    fun lateralError(position: Vec3d): Double

    /** Vertical deviation from the segment's expected y at the projected point. */
    fun verticalError(position: Vec3d): Double

    /** Remaining projected distance to the end node in blocks. */
    fun remainingDistance(position: Vec3d): Double

    /** Closest point on the segment used for debug rendering / telemetry. */
    fun closestPoint(position: Vec3d): Vec3d

    /**
     * Lookahead target on the segment used by the walk controller.
     */
    fun lookaheadPoint(position: Vec3d, lookaheadDistance: Double): Vec3d

    /**
     * Tests whether the given position is still close enough to treat the
     * segment as the current valid execution context.
     */
    fun containsPosition(
        position: Vec3d,
        corridorRadius: Double,
        verticalTolerance: Double,
        backtrackAllowance: Double,
        overshootAllowance: Double,
    ): Boolean

    /**
     * Tests whether the segment can be considered complete at the given
     * position. This is intentionally softer than an exact endpoint match.
     */
    fun hasReached(position: Vec3d, reachDistance: Double, verticalTolerance: Double): Boolean
}


abstract class LinearExecutionSegment(
	override val index: Int,
	final override val startPose: ExecutionPose,
	final override val endPose: ExecutionPose,
) : ExecutionSegment {

	protected val delta: Vec3d = this.endPose.position.subtract(this.startPose.position)
    protected val horizontalDelta = Vec3d(delta.x, 0.0, delta.z)
    protected val horizontalLengthSq = horizontalDelta.x * horizontalDelta.x + horizontalDelta.z * horizontalDelta.z
    override val horizontalLength: Double = sqrt(horizontalLengthSq)
    override val requiresVerticalMotion: Boolean = abs(delta.y) > EPSILON

    override fun projectedDistance(position: Vec3d): Double {
        if (horizontalLength <= EPSILON) return 0.0
        val relX = position.x - startPose.position.x
        val relZ = position.z - startPose.position.z
        return (relX * horizontalDelta.x + relZ * horizontalDelta.z) / horizontalLength
    }

    override fun lateralError(position: Vec3d): Double {
        val nearest = clampedPoint(position)
        return hypot(position.x - nearest.x, position.z - nearest.z)
    }

    override fun verticalError(position: Vec3d): Double = position.y - clampedPoint(position).y

    override fun remainingDistance(position: Vec3d): Double {
        val clampedDistance = projectedDistance(position).coerceIn(0.0, horizontalLength)
        return (horizontalLength - clampedDistance).coerceAtLeast(0.0)
    }

    override fun closestPoint(position: Vec3d): Vec3d = clampedPoint(position)

    override fun lookaheadPoint(position: Vec3d, lookaheadDistance: Double): Vec3d {
        if (horizontalLength <= EPSILON) return endPose.position
        val targetDistance = (projectedDistance(position).coerceAtLeast(0.0) + lookaheadDistance).coerceIn(0.0, horizontalLength)
        return pointAtDistance(targetDistance)
    }

    override fun containsPosition(
        position: Vec3d,
        corridorRadius: Double,
        verticalTolerance: Double,
        backtrackAllowance: Double,
        overshootAllowance: Double,
    ): Boolean {
        val projectedDistance = projectedDistance(position)
        return projectedDistance >= -backtrackAllowance
            && projectedDistance <= horizontalLength + overshootAllowance
            && lateralError(position) <= corridorRadius
            && abs(verticalError(position)) <= verticalTolerance
    }

    override fun hasReached(position: Vec3d, reachDistance: Double, verticalTolerance: Double): Boolean {
        val horizontalDistanceToEnd = hypot(position.x - endPose.position.x, position.z - endPose.position.z)
        if (horizontalDistanceToEnd <= reachDistance && abs(position.y - endPose.position.y) <= verticalTolerance) return true
        return remainingDistance(position) <= reachDistance
            && lateralError(position) <= reachDistance
            && abs(verticalError(position)) <= verticalTolerance
    }

    protected fun clampedPoint(position: Vec3d): Vec3d {
        if (horizontalLength <= EPSILON) return endPose.position
        val distance = projectedDistance(position).coerceIn(0.0, horizontalLength)
        return pointAtDistance(distance)
    }

    protected fun pointAtDistance(distance: Double): Vec3d {
        if (horizontalLength <= EPSILON) return endPose.position
        val t = (distance / horizontalLength).coerceIn(0.0, 1.0)
        return Vec3d(
            startPose.position.x + delta.x * t,
            startPose.position.y + delta.y * t,
            startPose.position.z + delta.z * t,
        )
    }

    companion object {
        private const val EPSILON = 1.0E-9
    }
}

/**
 * Initial production segment for same-plane traversals.
 *
 * It is intentionally always safe to cancel because there is no jump state or
 * committed world interaction yet.
 */
data class WalkSegment(
    override val index: Int,
    val start: ExecutionPose,
    val end: ExecutionPose,
) : LinearExecutionSegment(index, start, end) {
    /** +1 step-up, negative for step-down/drop depth, 0 flat. */
    val verticalStep: Int = Math.round(delta.y).toInt()
    override val typeName: String = when {
        verticalStep > 0 -> "Walk(+$verticalStep)"
        verticalStep < 0 -> "Walk($verticalStep)"
        else -> "Walk"
    }
    override val safeToCancel: Boolean = true
    // Walk segments handle a single-block step up (executor schedules a jump
    // input) and walk-off drops of any planned depth (gravity does the work).
    // Multi-block rises still fall to UnsupportedSegment.
    override val supportedByController: Boolean = delta.y <= 1.0 + 1.0E-6

    /**
     * Step segments are discrete: the player is either on the lower floor
     * (pre-jump / pre-drop) or the higher floor (post-jump / post-arrival).
     * Linear y interpolation along the segment is wrong here — at the jump
     * apex the player's y is well above the linear midpoint, which would make
     * the segment lose containment and the executor go "Lost".
     *
     * For a step segment the expected y is whichever of startY or endY is
     * closer to the player's current y. Plus a generous transient envelope
     * around the jump apex (~1.25 blocks above start) so mid-jump positions
     * stay within tolerance.
     */
    override fun verticalError(position: Vec3d): Double {
        if (verticalStep == 0) return super.verticalError(position)
        val toStart = position.y - startPose.position.y
        val toEnd = position.y - endPose.position.y
        // Mid-jump arc: y can transiently rise up to ~1.25 above startY for a
        // standing jump. Treat anything in the [startY, max(startY,endY)+0.4]
        // band as zero-error during a step-up transition; mirror for drops.
        val low = minOf(startPose.position.y, endPose.position.y)
        val high = maxOf(startPose.position.y, endPose.position.y)
        if (position.y in (low - 0.05)..(high + 0.4)) return 0.0
        return if (abs(toStart) < abs(toEnd)) toStart else toEnd
    }

    /**
     * Step segments must wait for the player to actually be at the destination
     * y before advancing. The base implementation only requires
     * `|position.y - endY| <= verticalTolerance` (default 0.6) which fires
     * mid-fall and mid-jump because the player crosses through that band
     * while still airborne. Cascading premature advances is what makes
     * descending slopes stall — the executor "completes" each segment before
     * the player lands, the path runs out, and execution is lost.
     */
    override fun hasReached(
        position: Vec3d,
        reachDistance: Double,
        verticalTolerance: Double,
    ): Boolean {
        if (verticalStep == 0) return super.hasReached(position, reachDistance, verticalTolerance)
        val horizontalDistanceToEnd = hypot(position.x - endPose.position.x, position.z - endPose.position.z)
        // Tight y-band so we only count the segment as reached after the
        // player has actually arrived at the destination floor.
        val landedAtEnd = abs(position.y - endPose.position.y) <= 0.2
        return horizontalDistanceToEnd <= reachDistance && landedAtEnd
    }
}

/**
 * Placeholder segment emitted when the refined path contains motion that the
 * current execution layer does not support yet.
 *
 * This keeps the runtime architecture honest: unsupported segments stay visible
 * in the debug state instead of being silently executed as if they were normal
 * walking edges.
 */
data class UnsupportedSegment(
    override val index: Int,
    val start: ExecutionPose,
    val end: ExecutionPose,
    private val reason: String,
) : LinearExecutionSegment(index, start, end) {
    override val typeName: String = reason
    override val safeToCancel: Boolean = true
    override val supportedByController: Boolean = false
}
