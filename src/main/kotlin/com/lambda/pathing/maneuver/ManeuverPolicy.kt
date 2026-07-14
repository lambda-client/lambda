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

package com.lambda.pathing.maneuver

/**
 * The chain-maneuver control policy (WP3.3), shared verbatim by the
 * validation simulation and the executor — the single-source rule that
 * keeps "validated" and "executed" the same behavior.
 *
 * A chain is executed closed-loop, not as a fixed tick script: hold sprint
 * and forward along the chain line, jump on every grounded tick (the next
 * hop launches the instant a landing is touched, carrying momentum), and
 * cut forward input mid-air when the next landing is within the braking
 * lead — air drag (≈0.91/tick) sheds the speed a fast entry carries, which
 * is what makes the policy robust across the whole entry band instead of
 * correct at one exact speed.
 */
object ManeuverPolicy {
    /**
     * Mid-air braking lead, in ticks of current speed: closer than this to
     * the target landing, forward input is released and drag brakes. Sized
     * so a 3-block hop coasts nearly its whole flight (a sprint-jump's
     * launch boost alone overshoots a 3-block hop by ~0.7 without input —
     * measured via the chain validation sims); forward re-engages only
     * when genuinely undershooting.
     */
    const val BRAKE_LEAD_TICKS = 8.0

    /**
     * Brake lead for SINGLE discovered jumps. Shorter than the chain lead:
     * chains hop ≤3 blocks and re-launch, so early braking protects the
     * next takeoff; a single 3.5–4.3-block jump braked from 8 ticks out
     * sheds so much carry it lands in the gap (measured: the 3-gap edge
     * stopped validating entirely at lead 8).
     */
    const val SINGLE_BRAKE_LEAD_TICKS = 5.0

    /**
     * How far (horizontally, blocks) a grounded position may be from a
     * waypoint center and still count as standing on it — covers rim
     * landings, where the AABB rests on the block while the feet center
     * hangs past its edge.
     */
    const val WAYPOINT_TOLERANCE = 0.7

    /** Chain hops are validated and executed at sprint; ticks cap per chain. */
    const val MAX_CHAIN_TICKS = 40

    /**
     * The brake rule applies to SINGLE discovered jumps too, not just
     * chains: a fast entry sheds its excess speed mid-air and centers on
     * the landing instead of carrying a block past it. Discovery validation
     * and the executor both use it — validated == executed, the same
     * single-policy invariant the chains established.
     */
    fun shouldBrake(
        horizontalDistanceToTarget: Double,
        horizontalSpeed: Double,
        leadTicks: Double = BRAKE_LEAD_TICKS,
    ): Boolean = horizontalDistanceToTarget < horizontalSpeed * leadTicks

    /**
     * Gain of the mid-air lateral correction, in input units per block/tick of
     * sideways drift. Sized to saturate at the drift the takeoff gate still
     * permits (~0.06 b/t), so any launch the executor is willing to make is
     * one the correction can straighten out.
     */
    const val LATERAL_CORRECTION_GAIN = 20.0

    /**
     * Strafe input that cancels sideways drift during flight, given the
     * drift's component to the LEFT of the direction being flown.
     *
     * The executor already does this — its airborne MPC carries full-strafe
     * candidates and picks whichever lands closest. The validation sim did
     * not, so it flew every arc open-loop and let a lateral perturbation ride
     * all the way to the ground. Over a 10-tick flight an 0.04 b/t drift
     * integrates to ~0.44 blocks under air drag, which is more than a
     * single-block pad can absorb — so the robustness box rejected every 1x1
     * landing as unreachable, in principle, forever. Cancelling drift is what
     * a human does in mid-air, and it belongs in the shared policy so that
     * "validated" and "executed" keep meaning the same flight.
     */
    fun lateralCorrection(leftwardDrift: Double): Double =
        (leftwardDrift * LATERAL_CORRECTION_GAIN).coerceIn(-1.0, 1.0)
}
