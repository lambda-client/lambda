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

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot

/**
 * The one controller. Given a flight state and a landing target, it says what
 * the inputs are — and **both** the validating simulation and the live executor
 * drive it.
 *
 * This exists because "validated == executed" was, until now, a convention we
 * kept breaking rather than a property we enforced. The steering logic lived in
 * two places — `ManeuverDiscovery.simulateEntry` and `PathfinderExecutor` — and
 * every jump bug found in the July 2026 reliability round was a *divergence
 * between those two copies*:
 *
 *  - the validator flew open-loop (frozen yaw, `strafe = 0`) while the executor
 *    steered at the landing every tick and ran an airborne correction MPC. So
 *    the validator rejected jumps the executor could comfortably fly — hardest
 *    exactly where the landing is narrow, which made 1x1 pads unreachable *in
 *    principle*;
 *  - the validator never cancelled lateral drift, so the ±0.04 b/t perturbation
 *    its own robustness check injected rode all the way to the ground (~0.44
 *    blocks under air drag — more than a single-block pad can absorb);
 *  - sprint was hardcoded true in the validator while the executor decided it
 *    per segment, so short hops were validated as sprint jumps that overfly the
 *    pad at every entry speed.
 *
 * One object, no copies. If the flight is wrong, it is wrong in both places at
 * once — which is the only situation in which a simulation is worth anything.
 *
 * The measured physics this is built against ([JumpJacobian], W0): a jump's
 * landing distance is *exactly* linear in launch progress and entry speed,
 * `D(s, v) = D0 + s + k·v` with `k ≈ 4.6…5.8` depending on rise. That is the
 * search's business, not the controller's — the controller only has to fly the
 * profile the search chose, identically, in both worlds.
 */
object ManeuverController {

    /** Gait is part of the entry state, never a global preference. */
    enum class Gait { Walk, Sprint;
        val sprinting get() = this == Sprint
    }

    /** What to press this tick. Yaw is included: the launch boost fires along it. */
    data class Inputs(
        val forward: Double,
        val strafe: Double,
        val jump: Boolean,
        val sprint: Boolean,
        val rotation: Rotation,
    )

    /**
     * Inputs for one tick of a maneuver.
     *
     * @param position     current feet position
     * @param velocity     current velocity
     * @param onGround     grounded this tick
     * @param target       the landing being flown at
     * @param gait         the gait this maneuver was validated with
     * @param launch       true only on the tick the jump is pressed
     * @param brakeLead    mid-air braking lead (ticks of current speed); null disables
     * @param launchYaw    yaw to fire the launch boost along; defaults to the
     *                     bearing to the target from where we stand
     */
    fun inputs(
        position: Vec3d,
        velocity: Vec3d,
        onGround: Boolean,
        target: Vec3d,
        gait: Gait,
        launch: Boolean,
        brakeLead: Double? = ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS,
        launchYaw: Rotation? = null,
    ): Inputs {
        // Aim at the landing from where we actually ARE, every tick. On the
        // launch tick this is the jump line (the sprint-jump boost fires along
        // the commanded yaw, so a stale yaw skews the whole arc); afterwards it
        // is what converts a lateral offset back into progress toward the pad
        // instead of letting it ride to the ground.
        val aim = if (launch) (launchYaw ?: position.rotationTo(target)) else position.rotationTo(target)

        val distance = hypot(target.x - position.x, target.z - position.z)
        val speed = hypot(velocity.x, velocity.z)
        val braking = !onGround && !launch && brakeLead != null &&
            ManeuverPolicy.shouldBrake(distance, speed, brakeLead)

        return Inputs(
            forward = if (braking) 0.0 else 1.0,
            strafe = if (onGround || launch) 0.0 else lateralCorrection(position, velocity, target),
            jump = launch,
            sprint = gait.sprinting,
            rotation = aim,
        )
    }

    /**
     * Inputs for one tick of WALKING — pure pursuit toward a lookahead point.
     *
     * Walking is simulated too, not merely followed. A grid route is a bad
     * walking path: an 8-connected lattice zig-zags toward any off-axis
     * bearing, and its moves stop-and-turn where a player carries momentum. By
     * rolling this controller out we get the real trajectory — corners rounded
     * at whatever radius the physics allows, speed carried through them, and
     * any-angle lines the lattice cannot express. Cornering behaviour becomes an
     * *output* of the simulation instead of a hand-tuned rule.
     *
     * The lookahead distance is the corner-cutting knob: large lookahead rounds
     * corners wide and keeps speed, small lookahead tracks the corridor tightly.
     * It is one of the few things the solver actually branches on.
     */
    fun walkInputs(
        position: Vec3d,
        lookahead: Vec3d,
        gait: Gait,
        throttle: Double = 1.0,
    ): Inputs {
        val aim = position.rotationTo(lookahead)
        return Inputs(
            forward = throttle.coerceIn(0.0, 1.0),
            strafe = 0.0,
            jump = false,
            // Sprint needs sustained forward input; a throttled approach is not
            // a sprint, and pretending otherwise is how the executor came to
            // believe it was sprinting while creeping at 0.05 b/t.
            sprint = gait.sprinting && throttle > 0.5,
            rotation = aim,
        )
    }

    /**
     * Strafe that cancels sideways drift, in the facing frame (+1 = right).
     *
     * Air control is weak, so an uncorrected drift is not a rounding error: at
     * 0.04 b/t it integrates to ~0.44 blocks over a flight under the 0.91/tick
     * drag. Cancelling it is what a human does mid-air, and it is what makes a
     * single-block landing possible at all.
     */
    private fun lateralCorrection(position: Vec3d, velocity: Vec3d, target: Vec3d): Double {
        val heading = Vec3d(target.x - position.x, 0.0, target.z - position.z)
        if (heading.lengthSquared() < 1.0E-9) return 0.0
        val unit = heading.normalize()
        // Left of the direction being flown; strafe +1 pushes right, so a
        // positive leftward drift is opposed by a positive strafe.
        val leftward = velocity.x * -unit.z + velocity.z * unit.x
        return ManeuverPolicy.lateralCorrection(leftward)
    }
}
