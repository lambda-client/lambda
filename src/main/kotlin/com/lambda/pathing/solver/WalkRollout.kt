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
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.Rotation.Companion.slerpYaw
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot

/**
 * W2a — walking, simulated.
 *
 * The coarse grid supplies topology and a cost-to-go oracle. It does **not**
 * supply the path the player walks. This rolls the shared [ManeuverController]
 * forward, tick by tick, through the real physics, and returns the trajectory
 * the agent would actually trace along a corridor — together with its true cost
 * in ticks.
 *
 * Why bother, when we already have a path?
 *
 * - An 8-connected lattice **zig-zags** toward any off-axis bearing. Pure
 *   pursuit does not: it cuts the staircase into a straight line for free.
 *   (`PathRefiner` string-pulls geometrically; that is an approximation of what
 *   rolling the controller out does natively — and it gets retired here.)
 * - Grid moves **stop and turn**; a player carries momentum. A 90-degree corner
 *   taken at sprint is a real, simulated speed loss. Rolling it out means
 *   cornering behaviour is an *output* rather than a hand-tuned rule, which is
 *   the honest answer to "the planner plans turns that ignore real curve-taking
 *   dynamics".
 * - Walk cost becomes **simulated ticks**, not a table constant — the truth the
 *   optimistic coarse cost is a lower bound of.
 * - And critically for parkour: a jump's entry velocity is *whatever the walk
 *   delivered*. There is no jump solver without this.
 *
 * Pure and snapshot-fed: takes a [PlayerPhysicsProfile] and a
 * [SimulationEnvironment], never the live entity or world — so it is legal on
 * the planner worker.
 */
object WalkRollout {

    /** How far ahead on the corridor to steer. The corner-cutting knob. */
    const val DEFAULT_LOOKAHEAD = 1.8

    /**
     * A yaw slew of 180 deg/tick reaches any target in one tick — the wrapped
     * yaw delta is always within +-180 — so this is the "instant" actuator, and
     * it is what `RotationSettings.turnSpeed` reports when Instant Rotation is
     * on (the default).
     */
    const val INSTANT_TURN_SPEED = 180.0

    /** A corridor node this much above the feet is a step-up, not a walk. */
    private const val STEP_UP_MIN_RISE = 0.5
    private const val STEP_UP_TRIGGER_DISTANCE = 1.3

    /**
     * Below the node we are steering at by this much = we are falling, not
     * walking. A planned drop never trips it: the node is down there with us.
     */
    private const val FALL_FAIL_DEPTH = 1.5

    /** Ticks of current speed within which we coast to a stop on the goal. */
    private const val BRAKE_LEAD_TICKS = 6.0

    data class State(
        val position: Vec3d,
        val velocity: Vec3d,
        val onGround: Boolean,
    )

    data class Result(
        /** Tick-by-tick positions, starting at the entry state. */
        val points: List<Vec3d>,
        /**
         * Full per-tick state. The grounded entries near the corridor end are
         * the LAUNCH LATTICE: the only states a jump can actually fire from,
         * spaced one stride apart. Knowing them exactly is the whole reason to
         * simulate the walk — it turns "hope we arrive well" into "choose the
         * approach that arrives launchable".
         */
        val trace: List<State>,
        /**
         * The per-tick INPUTS. This is the plan: the executor presses these,
         * it does not re-decide them. A simulation used only to *score* a
         * decision the executor then makes for itself leaves two decision
         * makers in the loop, which is the disease, not the cure.
         */
        val inputs: List<ManeuverController.Inputs>,
        /** Set when a jump was flown and it landed on its target. */
        val landed: Boolean = false,
        /** Horizontal error at touchdown, when a jump was flown. */
        val landingError: Double? = null,
        /** Simulated cost. This is the *true* cost the coarse graph estimates. */
        val ticks: Int,
        val end: State,
        /** How far along the corridor we got (index of the last node passed). */
        val reachedIndex: Int,
        /** True when the corridor was walked to its end within the budget. */
        val completed: Boolean,
        /** Set when the rollout hit a wall — the line is not walkable as flown. */
        val collided: Boolean,
        /**
         * The trajectory ended below the path: we fell. A plan that falls is not
         * a plan, whatever else it scores well on, and the executor must never
         * press one.
         */
        val fell: Boolean = false,
    ) {
        val endSpeed: Double get() = hypot(end.velocity.x, end.velocity.z)
    }

    /**
     * Roll the controller along [corridor] from [start].
     *
     * @param lookahead pure-pursuit lookahead: larger rounds corners wider and
     *   holds more speed, smaller tracks the corridor tightly. The solver
     *   branches on this — it is how "cut this corner" is expressed.
     * @param arriveDistance how close to the final node counts as arrived.
     */
    fun roll(
        profile: PlayerPhysicsProfile,
        environment: SimulationEnvironment,
        start: State,
        corridor: List<Vec3d>,
        gait: ManeuverController.Gait,
        lookahead: Double = DEFAULT_LOOKAHEAD,
        maxTicks: Int = 120,
        arriveDistance: Double = 0.35,
        /**
         * Brake to a standstill on the final node. Arrival means *standing* on
         * the goal, not sailing through it, so the plan has to contain the stop
         * — full throttle to the last node overshoots and the traversal never
         * completes.
         */
        brakeAtEnd: Boolean = false,
        /**
         * The grounded tick to press JUMP on, or null to never press it.
         *
         * This is the entire jump interface, and it is deliberately this thin.
         * A jump is not a maneuver object with a takeoff and a landing that
         * someone else identified for us — it is one boolean, on one tick, and
         * the *only* decision in it is **when**. Everything a jump is for
         * (clearing a hole, reaching a ledge) is a consequence the physics
         * produces, and the search finds the tick that produces it by trying
         * ticks and keeping the trajectory that survives.
         */
        launchTick: Int? = null,
        /** Yaw the player is ACTUALLY facing at the entry state. */
        startYaw: Double = 0.0,
        /** Degrees per tick the yaw actuator can slew. See [applyYaw]. */
        turnSpeed: Double = INSTANT_TURN_SPEED,
    ): Result {
        if (corridor.isEmpty()) {
            return Result(
                listOf(start.position), listOf(start), emptyList(),
                ticks = 0, end = start, reachedIndex = 0, completed = true, collided = false,
            )
        }

        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = start.position,
                rotation = Rotation(startYaw, 0.0),
                velocity = start.velocity,
                onGround = start.onGround,
                isSprinting = gait.sprinting,
            ),
            skipEntityCollisions = true,
        )

        val points = ArrayList<Vec3d>(maxTicks + 1)
        val trace = ArrayList<State>(maxTicks + 1)
        val inputs = ArrayList<ManeuverController.Inputs>(maxTicks)
        points += start.position
        trace += start
        var index = 0
        var collided = false

        // The yaw ACTUATOR, not the yaw command.
        //
        // The controller asks to face the target. The client cannot face it: it
        // slews toward it at `turnSpeed` degrees per tick
        // (`RotationManager.updateActiveRotation` -> `slerpYaw`). Simulating the
        // command instead of the actuator is a lie in exactly the situations
        // that matter — a sharp approach, or a launch tick whose jump line the
        // player has not finished turning onto, where the sprint-jump boost then
        // fires along a heading the plan never simulated and the arc lands
        // somewhere the plan never predicted.
        //
        // With the actuator in the model, the *search* sees the cost of a turn:
        // it will choose a later launch tick, or a wider approach that is
        // already pointing the right way, entirely on its own. That is the
        // honest form of "plan turns that match real curve-taking dynamics" —
        // an emergent property of simulating the plant, not another hand-tuned
        // alignment gate bolted onto the executor.
        var facing = startYaw
        fun applyYaw(desired: Rotation): Rotation {
            facing = Rotation(facing, 0.0).slerpYaw(desired.yaw, turnSpeed)
            return Rotation(facing, desired.pitch)
        }

        fun result(
            ticks: Int, end: State, completed: Boolean,
            landed: Boolean = false, landingError: Double? = null,
            fell: Boolean = false,
        ) = Result(
            points = points, trace = trace, inputs = inputs, landed = landed,
            landingError = landingError, ticks = ticks, end = end,
            reachedIndex = index, completed = completed, collided = collided,
            fell = fell,
        )

        // ONE loop. There is no "walk phase" and no "flight phase" — there is a
        // player, a corridor, and a set of inputs per tick. Jump is one of those
        // inputs, pressed on the tick the SEARCH chose, and everything that
        // makes a jump a jump happens in the physics afterwards.
        //
        // Splitting this into a walk branch and a flight branch (with a landing
        // handed in from outside) is what forced someone upstream to decide
        // "this segment is a jump" before the simulator ever ran — and whatever
        // decides that is a second planner, with its own opinions, its own bugs,
        // and its own disagreements with the physics. There is only one planner.
        for (tick in 0 until maxTicks) {
            val before = simulator.lastTick
            index = advance(before.position, corridor, index, arriveDistance)

            val node = corridor[index]
            val airborne = !before.onGround
            val launch = launchTick != null && tick == launchTick && before.onGround

            // Arrived: grounded on the last node. (Airborne over it is not
            // arrival — it is mid-fall, and the tick after may be anywhere.)
            if (!airborne && index >= corridor.lastIndex &&
                horizontal(before.position, corridor.last()) <= arriveDistance
            ) {
                return result(tick, State(before.position, before.velocity, before.onGround), completed = true)
            }

            // A rise the player is walking into is a step, and a step is the
            // controller's business, not the search's: it is deterministic, it
            // needs no launch tick, and searching it would make every staircase
            // a combinatorial problem. The search exists for the jumps physics
            // does NOT hand us for free.
            val stepUp = !airborne && !launch &&
                node.y - before.position.y > STEP_UP_MIN_RISE &&
                horizontal(before.position, node) <= STEP_UP_TRIGGER_DISTANCE

            val step = when {
                // Airborne, or the tick we leave the ground: fly at the node.
                // Steering at the NODE (not the corner-cutting lookahead) is
                // what converts a lateral offset into progress toward the pad,
                // and the brake is what stops us sailing over it.
                airborne || launch || stepUp -> ManeuverController.inputs(
                    position = before.position,
                    velocity = before.velocity,
                    onGround = before.onGround,
                    target = node,
                    gait = gait,
                    launch = launch || stepUp,
                    launchYaw = if (launch || stepUp) before.position.rotationTo(node) else null,
                )
                else -> {
                    val toEnd = horizontal(before.position, corridor.last())
                    val speed = hypot(before.velocity.x, before.velocity.z)
                    val braking = brakeAtEnd && index >= corridor.lastIndex &&
                        toEnd <= speed * BRAKE_LEAD_TICKS
                    ManeuverController.walkInputs(
                        position = before.position,
                        lookahead = lookaheadPoint(before.position, corridor, index, lookahead),
                        gait = gait,
                        throttle = if (braking) 0.0 else 1.0,
                    )
                }
            }

            inputs += step
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = step.forward, strafe = step.strafe, jump = step.jump, sneak = false,
                    sprint = step.sprint, useItemSlowdown = false, rotation = applyYaw(step.rotation),
                )
            )
            points += current.position
            trace += State(current.position, current.velocity, current.onGround)
            if (current.simulator.state.horizontalCollision) collided = true

            // FELL. Below the node we are steering at, with nothing under us.
            // No hole detector, no gap classifier: the floor either held or it
            // did not, and the simulator is the one that finds out. A planned
            // drop never trips this, because the node we steer at is already
            // down there with us.
            if (current.position.y < node.y - FALL_FAIL_DEPTH) {
                return result(tick + 1, trace.last(), completed = false, fell = true)
            }
        }

        return result(maxTicks, trace.last(), completed = false)
    }

    /** Feet within this of the landing centre counts as landed on it. */
    const val LANDING_TOLERANCE = 0.6
    const val LANDING_VERTICAL_TOLERANCE = 0.35

    /** Advance the corridor cursor past every node we have already reached. */
    private fun advance(
        position: Vec3d,
        corridor: List<Vec3d>,
        from: Int,
        arriveDistance: Double,
    ): Int {
        var index = from
        while (index < corridor.lastIndex && horizontal(position, corridor[index]) <= arriveDistance) {
            index++
        }
        return index
    }

    /**
     * The pure-pursuit target: walk [lookahead] blocks forward along the
     * corridor polyline from the current cursor.
     *
     * Looking *through* corners is exactly what straightens the lattice
     * zig-zag and lets the agent hold speed round a bend — the trajectory bows
     * inside the polyline instead of tracking its every step.
     */
    private fun lookaheadPoint(
        position: Vec3d,
        corridor: List<Vec3d>,
        index: Int,
        lookahead: Double,
    ): Vec3d {
        var remaining = lookahead
        var current = position
        var i = index
        while (i <= corridor.lastIndex) {
            val node = corridor[i]
            val step = horizontal(current, node)
            if (step >= remaining) {
                if (step < 1.0E-6) return node
                val t = remaining / step
                return Vec3d(
                    current.x + (node.x - current.x) * t,
                    node.y,
                    current.z + (node.z - current.z) * t,
                )
            }
            remaining -= step
            current = node
            i++
        }
        return corridor.last()
    }

    private fun horizontal(a: Vec3d, b: Vec3d): Double = hypot(a.x - b.x, a.z - b.z)
}
