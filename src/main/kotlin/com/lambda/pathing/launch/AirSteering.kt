/*
 * Copyright 2026 Lambda
 */
package com.lambda.pathing.launch

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Closed-loop landing correction for the air phase of a jump. Air drag is 0.91, so
 * mid-flight inputs move the landing by more than a block over a typical arc; each tick
 * the remaining flight is known (vertical motion is uncontrolled), the drift landing is a
 * geometric series away ([HorizontalDynamics.air]), and the input that best moves it onto
 * the aim point is one comparison over the eight key directions.
 *
 * Only vanilla-legal control is emitted: key combinations with components in {-1, 0, 1},
 * never a fractional throttle; magnitude is expressed by WHICH remaining ticks press,
 * re-decided every tick. The correction is a delta against the planned hold schedule: on
 * course, the caller keeps its planned inputs byte-for-byte, so a certified tape whose arc
 * model was exact is unchanged.
 */
object AirSteering {
    data class Keys(val forward: Double, val strafe: Double)

    /**
     * Everything a follower needs to fly one solved launch closed-loop: the world-frame
     * aim point ([LaunchSolution.aimDistance] along the gap from the takeoff cell
     * centre), the gap bearing the hold schedule accelerates along, the arc's tick
     * count, and the planned hold length (zero when the solution releases at launch).
     */
    data class AirPlan(
        val aimX: Double,
        val aimZ: Double,
        val unitX: Double,
        val unitZ: Double,
        val airTicks: Int,
        val holdTicks: Int,
        val sprintAcceleration: Double,
        val walkAcceleration: Double,
    )

    /**
     * The input for this tick, or null when the planned schedule should proceed
     * unmodified (on target, no authority left, or the plan is already the best move).
     *
     * [remainingTicks] is flight ticks left including this one. [plannedHoldTicks] is
     * how many of those the schedule would press forward along ([unitX], [unitZ]).
     * Accelerations are per-tick air accelerations (already input-damped).
     */
    fun steer(
        positionX: Double,
        positionZ: Double,
        velocityX: Double,
        velocityZ: Double,
        yawDegrees: Double,
        aimX: Double,
        aimZ: Double,
        unitX: Double,
        unitZ: Double,
        remainingTicks: Int,
        plannedHoldTicks: Int,
        sprintAcceleration: Double,
        walkAcceleration: Double,
    ): Keys? {
        if (remainingTicks <= 0) return null
        val reach = displacementKernel(remainingTicks)

        // Landing under the planned schedule: drift plus the scheduled forward holds.
        val holds = plannedHoldTicks.coerceIn(0, remainingTicks)
        var plannedAlong = 0.0
        for (slot in 1..holds) plannedAlong += displacementKernel(remainingTicks - slot + 1)
        val plannedX = positionX + velocityX * reach + sprintAcceleration * unitX * plannedAlong
        val plannedZ = positionZ + velocityZ * reach + sprintAcceleration * unitZ * plannedAlong

        val errorX = aimX - plannedX
        val errorZ = aimZ - plannedZ
        if (hypot(errorX, errorZ) <= ON_TARGET_BLOCKS) return null

        // This tick's planned contribution, to be swapped for a candidate's.
        val plannedTickX = if (holds >= 1) sprintAcceleration * unitX else 0.0
        val plannedTickZ = if (holds >= 1) sprintAcceleration * unitZ else 0.0

        val radians = Math.toRadians(yawDegrees)
        val sin = sin(radians)
        val cos = cos(radians)

        var bestForward = if (holds >= 1) 1.0 else 0.0
        var bestStrafe = 0.0
        var bestError = hypot(errorX, errorZ)
        var improved = false
        for (forward in COMPONENTS) {
            for (strafe in COMPONENTS) {
                // World direction of this key combo at the current facing; diagonals
                // recover full magnitude in 1.21.11, so every non-empty combo is unit.
                var directionX = strafe * cos - forward * sin
                var directionZ = forward * cos + strafe * sin
                val length = hypot(directionX, directionZ)
                if (length > 1e-9) {
                    directionX /= length
                    directionZ /= length
                }
                // Releasing forward drops sprint, and strafe-only ticks accelerate at
                // the walking rate.
                val acceleration = when {
                    length <= 1e-9 -> 0.0
                    forward > 0.0 -> sprintAcceleration
                    else -> walkAcceleration
                }
                val candidateX = errorX - reach * (acceleration * directionX - plannedTickX)
                val candidateZ = errorZ - reach * (acceleration * directionZ - plannedTickZ)
                val error = hypot(candidateX, candidateZ)
                if (error < bestError - 1e-9) {
                    bestError = error
                    bestForward = forward
                    bestStrafe = strafe
                    improved = true
                }
            }
        }
        return if (improved) Keys(bestForward, bestStrafe) else null
    }

    /**
     * Blocks of displacement one unit of velocity held now produces over [ticks] ticks
     * of drag: the geometric series 0.91 + 0.91^2 + ... counted from the pre-drag
     * displacement convention the simulator uses (this tick's move happens before this
     * tick's drag, so the first term is 1).
     */
    private fun displacementKernel(ticks: Int): Double {
        var sum = 0.0
        var factor = 1.0
        repeat(ticks) {
            sum += factor
            factor *= HorizontalDynamics.AIR_DRAG
        }
        return sum
    }

    /**
     * Within this landing error the schedule proceeds unmodified; half the solver's
     * `LANDING_SAFETY_BLOCKS`, so scatter inside it still lands with margin.
     * See docs/decisions/launch-solver.md (air-steering deadband).
     */
    private const val ON_TARGET_BLOCKS = 0.1

    private val COMPONENTS = doubleArrayOf(-1.0, 0.0, 1.0)
}
