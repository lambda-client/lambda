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

package com.lambda.pathing.solver

import com.lambda.pathing.maneuver.ManeuverController
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationEnvironment
import net.minecraft.util.math.Vec3d

/**
 * W2a — choose the walking line by *simulating* the candidates.
 *
 * Rolling the controller out once only predicts. The point of having real
 * physics in the loop is to **search**: try several lines, keep the one the
 * simulator says is fastest and doesn't hit anything.
 *
 * The single most important knob is the pure-pursuit **lookahead**, because it
 * is how "cut this corner" is expressed. A large lookahead looks through the
 * bend, bows inside the polyline and carries speed; a small one tracks the
 * lattice faithfully and scrubs speed at every 45-degree kink. Neither is right
 * in general — a wide line is faster in the open and clips the wall in a
 * corridor — which is exactly why it should be *simulated per situation*
 * instead of set to one hand-tuned constant in the config, as it is today.
 *
 * The gait is searched with it: sprint is not a preference but a property of
 * the line. (Today `riseWithin` blanket-kills sprint 2.5 blocks before *any*
 * rise, so a staircase is walked at a crawl — the field logs show takeoffs at
 * `entry v = 0.000`. The simulator can simply be asked whether sprinting that
 * stretch works.)
 *
 * Cost is **simulated ticks** — the truth the coarse graph's constant is an
 * optimistic lower bound of.
 *
 * Cheap by construction: a candidate is ~100 physics ticks, and there are a
 * handful. Everything continuous — steering, braking, drift cancellation — is
 * delegated to the shared [ManeuverController] and rolled out, never searched.
 */
object WalkSolver {

    /** Corner-cutting candidates: tight tracking through to a wide racing line. */
    val LOOKAHEADS = doubleArrayOf(1.0, 1.8, 2.6, 3.6)

    data class Plan(
        val lookahead: Double,
        val gait: ManeuverController.Gait,
        val rollout: WalkRollout.Result,
        /**
         * The launch this approach was PROVEN to produce, when the corridor
         * ends at a jump. Null means: flying this line, no grounded state on
         * the approach can land the jump. That approach is not merely slow —
         * it is a fall waiting to happen, and it must lose to any approach that
         * has one.
         */
        val launch: JumpProbe.Launch? = null,
    ) {
        val points: List<Vec3d> get() = rollout.points
        val ticks: Int get() = rollout.ticks
    }

    /**
     * Best line over [corridor] from [start].
     *
     * Ranking, in order:
     *  1. **completed beats incomplete** — a line that does not get there is
     *     not a faster line, however good it looks early;
     *  2. **clean beats scraping** — a rollout that grinds a wall is tracking
     *     geometry it should have gone around;
     *  3. **fewest simulated ticks**;
     *  4. tie-break on carried speed, because the exit velocity of a walk is
     *     the *entry velocity of the next jump*. A line that arrives a tick
     *     later but 0.03 b/t faster is often the one that makes the jump —
     *     0.03 b/t is ~0.16 blocks of landing distance (W0: dD/dv ~ 5.3).
     */
    fun solve(
        profile: PlayerPhysicsProfile,
        environment: SimulationEnvironment,
        start: WalkRollout.State,
        corridor: List<Vec3d>,
        gaits: List<ManeuverController.Gait> = listOf(
            ManeuverController.Gait.Sprint,
            ManeuverController.Gait.Walk,
        ),
        maxTicks: Int = 260,
        brakeAtEnd: Boolean = false,
        /** Yaw the player is actually facing right now. */
        startYaw: Double = 0.0,
        /** The yaw actuator's slew rate — see [WalkRollout.INSTANT_TURN_SPEED]. */
        turnSpeed: Double = WalkRollout.INSTANT_TURN_SPEED,
    ): Plan? {
        if (corridor.isEmpty()) return null

        // Airborne entry needs no special case: with no launch tick to press,
        // the rollout simply flies at the next node — brake, drift-cancel and
        // all — because that is what the controller does when it is not on the
        // ground. One code path, in the air and on it.

        // WALK IT. No jump. If the floor holds all the way, this is the plan
        // and there was never a jump here to find.
        //
        // If it does not hold, the rollout falls — and that is the only signal
        // this solver needs. Nothing classified the terrain; the simulator
        // simply walked the line we were about to walk and found out what
        // happens. What comes back is a trace of grounded ticks, and those are
        // the LAUNCH LATTICE: the only ticks a jump can be pressed on, one
        // stride (0.22 walk / 0.28 sprint) apart. A correction smaller than one
        // stride is not available at any tick — it has to come from a different
        // approach — which is exactly why the approach is searched alongside the
        // tick, and not tuned at the lip.
        var best: Plan? = null
        for (gait in gaits) {
            for (lookahead in LOOKAHEADS) {
                val probe = WalkRollout.roll(
                    profile, environment, start, corridor, gait, lookahead, maxTicks,
                    brakeAtEnd = brakeAtEnd, startYaw = startYaw, turnSpeed = turnSpeed,
                )
                best = pick(Plan(lookahead, gait, probe, null), best)
                if (probe.completed) continue

                // Press JUMP on each grounded tick and see which trajectory
                // survives. That is the whole search: one boolean, one tick.
                for ((tick, state) in probe.trace.withIndex()) {
                    if (!state.onGround) continue
                    val full = WalkRollout.roll(
                        profile, environment, start, corridor, gait, lookahead, maxTicks,
                        brakeAtEnd = brakeAtEnd, launchTick = tick,
                        startYaw = startYaw, turnSpeed = turnSpeed,
                    )
                    val launch = if (!full.fell && full.reachedIndex > probe.reachedIndex) {
                        JumpProbe.Launch(tick, state, full.landingError ?: 0.0, full.ticks)
                    } else null
                    best = pick(Plan(lookahead, gait, full, launch), best)
                }
            }
        }
        return best
    }

    private fun pick(candidate: Plan, incumbent: Plan?): Plan =
        if (incumbent == null || better(candidate, incumbent)) candidate else incumbent

    private const val DEFAULT_FLIGHT_LOOKAHEAD = WalkRollout.DEFAULT_LOOKAHEAD


    /**
     * Reliability first, speed last.
     *
     * Scoring the walk on ticks alone is what made the simulator-chosen line
     * 3.8% SLOWER on the suite: it picked a wide racing line that arrived fast
     * and arrived *unlaunchable*, and the jump then paid for it. Speed is not
     * the objective. **Arriving somewhere the next maneuver can be made from is
     * the objective.**
     *
     * So the order is: don't fall; get there; don't scrape; be quick; and only
     * then carry speed — because the exit velocity of a walk is the entry
     * velocity of the next jump, and 0.03 b/t of it is ~0.16 blocks of landing
     * distance (W0: dD/dv ~ 5.3).
     */
    private fun better(candidate: Plan, incumbent: Plan): Boolean {
        val a = candidate.rollout
        val b = incumbent.rollout

        // A trajectory that falls is not a slow plan, it is not a plan.
        if (a.fell != b.fell) return !a.fell
        if (a.completed != b.completed) return a.completed
        if (a.reachedIndex != b.reachedIndex) return a.reachedIndex > b.reachedIndex
        if (a.collided != b.collided) return !a.collided
        if (a.ticks != b.ticks) return a.ticks < b.ticks
        return a.endSpeed > b.endSpeed
    }
}
