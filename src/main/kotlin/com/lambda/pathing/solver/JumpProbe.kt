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
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationEnvironment
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Fire a jump from an *exactly known* state and see where it lands.
 *
 * This is the payoff of simulating the walk. The approach rollout gives us the
 * real launch lattice — the handful of grounded states the agent will actually
 * pass through, one stride apart — and this fires a jump from each of them. So
 * instead of arriving at a lip and *hoping* the entry state works, the approach
 * is chosen because it was proven to produce a launchable state.
 *
 * That is the difference between the current system and a reliable one. Today
 * the executor discovers at the lip that its entry speed is 0.099, refuses (or
 * worse, does not refuse), and falls. Here the same fact is known *before the
 * approach is committed*, while there is still time to walk it differently.
 *
 * The measured physics (W0) says the miss is exactly linear in launch progress
 * and entry speed, `D = D0 + s + k·v`, and monotone in both — so the best launch
 * tick along a fixed approach could be bisected. We enumerate instead: the
 * lattice has only a handful of grounded states in range, enumeration cannot be
 * fooled by a head bonk or a clipped mid column, and the simulator stays the
 * decider. The linear model is an accelerator to reach for when this shows up
 * in a profile, never a correctness dependency.
 */
object JumpProbe {

    /** Feet within this of the landing centre counts as landed on it. */
    const val LANDING_TOLERANCE = 0.6
    const val LANDING_VERTICAL_TOLERANCE = 0.35

    private const val MAX_FLIGHT_TICKS = 30

    data class Launch(
        /** Index into the approach trace: WHICH grounded tick to jump on. */
        val traceIndex: Int,
        val state: WalkRollout.State,
        /** Horizontal distance from the landing centre at touchdown. */
        val error: Double,
        val flightTicks: Int,
    )

    /**
     * Best launch over the grounded states of [trace] that lie within
     * [searchRadius] of [takeoff] — i.e. over the launch lattice this approach
     * actually produces. Null when no state on it lands the jump: that is not a
     * tuning problem, it is the approach being wrong, and the caller should pick
     * a different approach rather than launch anyway.
     */
    fun bestLaunch(
        profile: PlayerPhysicsProfile,
        environment: SimulationEnvironment,
        trace: List<WalkRollout.State>,
        takeoff: Vec3d,
        landing: Vec3d,
        gait: ManeuverController.Gait,
        searchRadius: Double = 1.6,
    ): Launch? {
        var best: Launch? = null
        trace.forEachIndexed { index, state ->
            if (!state.onGround) return@forEachIndexed
            if (hypot(state.position.x - takeoff.x, state.position.z - takeoff.z) > searchRadius) {
                return@forEachIndexed
            }
            val result = fire(profile, environment, state, landing, gait) ?: return@forEachIndexed
            if (best == null || result.first < best.error) {
                best = Launch(index, state, result.first, result.second)
            }
        }
        return best
    }

    /**
     * One jump from [from] toward [landing], flown by the shared controller.
     * Returns (horizontal error at touchdown, flight ticks), or null if it never
     * lands on the target — short into the gap, blocked, or overshot past it.
     */
    fun fire(
        profile: PlayerPhysicsProfile,
        environment: SimulationEnvironment,
        from: WalkRollout.State,
        landing: Vec3d,
        gait: ManeuverController.Gait,
    ): Pair<Double, Int>? {
        val launchYaw = from.position.rotationTo(landing)
        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = from.position,
                rotation = launchYaw,
                velocity = from.velocity,
                onGround = true,
                isSprinting = gait.sprinting,
            ),
            skipEntityCollisions = true,
        )

        for (tick in 0 until MAX_FLIGHT_TICKS) {
            val before = simulator.lastTick
            val inputs = ManeuverController.inputs(
                position = before.position,
                velocity = before.velocity,
                onGround = before.onGround,
                target = landing,
                gait = gait,
                launch = tick == 0,
                launchYaw = launchYaw,
            )
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = inputs.forward,
                    strafe = inputs.strafe,
                    jump = inputs.jump,
                    sneak = false,
                    sprint = inputs.sprint,
                    useItemSlowdown = false,
                    rotation = inputs.rotation,
                )
            )
            if (current.simulator.state.horizontalCollision) return null
            if (current.onGround && tick > 1) {
                val error = hypot(current.position.x - landing.x, current.position.z - landing.z)
                val vertical = abs(current.position.y - landing.y)
                return if (error <= LANDING_TOLERANCE && vertical <= LANDING_VERTICAL_TOLERANCE) {
                    error to (tick + 1)
                } else {
                    null
                }
            }
            // Fell below the landing without reaching it: short, into the gap.
            if (current.position.y < landing.y - 1.0) return null
        }
        return null
    }
}
