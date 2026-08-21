/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.JumpArcProbe
import com.lambda.pathing.coarse.Stance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What a body standing on an anchor is allowed to try next.
 *
 * Pure: it reads the value field and the configured limits and returns decisions. It holds
 * no search state, so a change to the vocabulary cannot disturb the frontier, the horizon
 * or the endgame.
 */
internal class ActionSet(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
) {

    /**
     * The action family out of one anchor: the cheapest few coarse steps by
     * `edge + value`, each in the walk styles and as a launch lattice.
     *
     * This is the direct replacement for `route.edges[nodeIndex]`. The route offered
     * one committed step; the field offers the [ValueFieldSearchConfig.branchingSteps]
     * best and lets the physics decide between them.
     */
    fun actions(anchor: ValueAnchor, hazardFrame: Int?): List<TrajectoryDecision> {
        val steps = field.steps(
            anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
            anchor.heading(),
        )
        if (steps.isEmpty()) return emptyList()

        val actions = ArrayList<TrajectoryDecision>()
        val walks = ArrayList<TrajectoryDecision>()
        val launches = ArrayList<TrajectoryDecision>()
        for (sprint in config.sprintModes) {
            for (step in steps) {
                for ((lookAhead, ease) in WALK_STYLES) {
                    walks += TrajectoryDecision.Walk(sprint, step.to, lookAhead, ease)
                }
            }
        }
        // The off-lattice family, fanned around the bearing the value field is
        // descending toward. Offered from the best step only: a fan around a
        // second-choice direction is a fan around the wrong idea, and the second
        // choice gets its own fan once it has earned an anchor of its own.
        val bearing = descentBearing(anchor, steps.first().to)
        val target = steps.first().to
        val jumpFirst = steps.first().kind == CoarseMoveKind.JUMP_CANDIDATE
        for (sprint in config.sprintModes) {
            // The committed run along the bearing is always worth having: holding one
            // heading is what lets the body build speed instead of re-deciding every
            // block. Deviating from it is not. A fanned offset is a detour that must
            // later be corrected, and on a straight fourteen-block run the fan alone
            // cost 167 degrees of turning and 11% of extra distance. It earns its
            // place only where the straight line has been *shown* to fail, or where
            // the route is turning anyway.
            actionsForOffsets(anchor, sprint, target, bearing, walks)
            // Strafe is not a way to deviate — binary keys deviate by a blunt 45
            // degrees, where the yaw fan aims in twelves, and offering it that way
            // measured worse. Its value is *decoupling*: hold the same line of travel
            // while the body faces somewhere else, pre-aiming for the turn that comes
            // next. So it is offered only where the route actually turns; on a
            // straight run it is a strictly worse way to go forwards, and under a
            // fixed expansion budget every action offered costs search depth
            // elsewhere. Blanket-offering these measured worse on the corpus.
            if (turnsAhead(anchor, steps.first().to)) {
                for ((keys, facingOffset) in DECOUPLED_FACINGS) {
                    walks += TrajectoryDecision.Heading(
                        sprint, target, bearing + facingOffset, facingOffset,
                        delayFrames = null, keys = keys,
                    )
                }
            }
        }

        // No launches once the goal is inside braking range. A jump cannot help a
        // body stop, it commits ticks of airborne time it cannot steer out of, and
        // the terminal sweep owns the arrival anyway — so every launch offered here
        // is a rollout spent proving it made things worse. This is the visible
        // "jumping around for ages before it stops".
        if (field.guide(anchor.stance) <= searchConfig.finishValueTicks) return actions

        // A gap taken off-axis is the case the grid cannot describe at all: leaving a
        // pad at an angle to line the body up for the jump after it.
        for (sprint in config.sprintModes) {
            // Launches along the bearing are always available — on flat ground a
            // sprint-jump chain is genuinely faster than running. Launches at an
            // *angle* are the same trap as the walk offsets and were left ungated:
            // on a straight fourteen-block run the search took a -12 degree hop at
            // frame 7, ended up a block and a half off the line, and spent 130
            // degrees of turning at the far end coming back to the goal. Angled hops
            // are for terrain that turns, not for open ground.
            for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                launches += TrajectoryDecision.Heading(sprint, target, bearing, 0.0, delayFrames = delay)
            }
            if (anchor.hazardFrame != null || turnsAhead(anchor, target)) {
                for (offset in searchConfig.headingFanDegrees) {
                    if (offset == 0.0) continue
                    for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing + offset, offset, delayFrames = delay,
                        )
                    }
                }
            }
            // Air control is offered only once a plain launch has been seen to fail:
            // the arc is otherwise fully committed at takeoff, and 0.026 a tick of
            // in-flight steering is exactly what rescues a jump that lands short or a
            // little wide. Offering it everywhere spends the budget proving that a
            // jump which was already going to land lands anyway.
            if (hazardFrame != null) {
                for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                    for (air in AIRBORNE_KEYS.drop(1)) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing, 0.0, delayFrames = delay,
                            keys = MovementKeys.FORWARD, airborneKeys = air,
                        )
                    }
                }
            }
        }

        // Launches are offered on the two most promising steps only: a launch is
        // seven simulations, and a third-choice direction that also needs a jump is
        // reachable through the anchor its own best step creates.
        for (sprint in config.sprintModes) {
            for (step in steps.take(LAUNCH_STEPS)) {
                if (!canReach(anchor, step.to, sprint)) continue
                // A pad well inside reach is one the body flies past, and landing long
                // on a one-block pad is exactly as fatal as landing short. Only there
                // is a shed worth simulating: at the edge of reach every tick of speed
                // is needed, and offering a brake costs search depth to prove it.
                val sheds = if (searchConfig.offerBrakeTicks && overshoots(anchor, step.to, sprint)) {
                    BRAKE_TICKS
                } else {
                    NO_BRAKE
                }
                for (delay in launchDelays(anchor, step)) {
                    for (brake in sheds) {
                        launches += TrajectoryDecision.Launch(sprint, step.to, delay, brake)
                    }
                }
            }
        }
        // Order decides everything now. The search dives — it simulates an anchor's
        // *first* action and follows the successor — so whatever leads this list is
        // what actually gets tried, and the rest are fallbacks reached only if the
        // line stalls. Putting every walk ahead of every launch made the search walk
        // into a gap forever and never once press jump on a parkour it used to cross.
        // The coarse layer already knows which it is: if the step it wants is a jump
        // candidate, a launch is the primary action and walking is the fallback.
        actions += if (jumpFirst) launches + walks else walks + launches
        return actions
    }

    /** Whether the value-descending line turns materially within the next two steps. */
    private fun turnsAhead(anchor: ValueAnchor, firstStep: Stance): Boolean {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD + 1, anchor.heading())
        if (chain.size < 3) return false
        val first = bearingBetween(chain[0].center(), chain[1].center())
        val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
        return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
    }

    /**
     * The direction the value field is descending in, as a continuous bearing from
     * where the body actually stands — not a bearing between two block centres.
     *
     * Aimed a couple of steps out rather than at the immediate neighbour, so the fan
     * is spread around the line the route is going, not around one lattice step.
     */
    private fun descentBearing(anchor: ValueAnchor, firstStep: Stance): Double {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD, anchor.heading())
        val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
        return bearingBetween(
            HorizontalPoint(anchor.state.position.x, anchor.state.position.y, anchor.state.position.z),
            aim,
        )
    }

    /**
     * Whether a jump from this body could reach [target] at all.
     *
     * Seven launch rollouts per step were being spent proving arcs that fall short by
     * a block, which the measured reach model answers for free. The entry speed is the
     * best the body could have by takeoff — its current speed, or the gait's
     * equilibrium if it is still accelerating — so the filter is optimistic and can
     * only discard jumps that are physically out of range, never ones the simulator
     * would have certified.
     */
    /**
     * Whether a full-speed jump would carry the body past [target] rather than onto it.
     *
     * The same measured reach model as [canReach], read from the other end: a pad that
     * sits well inside what this entry speed can throw the body is a pad the arc
     * overshoots, and shedding a tick of speed is the only lever that fixes it.
     */
    private fun overshoots(anchor: ValueAnchor, target: Stance, sprint: Boolean): Boolean {
        val entrySpeed = maxOf(anchor.speed, if (sprint) SPRINT_TOP_SPEED else WALK_TOP_SPEED)
        val distance = hypot(
            target.x + 0.5 - anchor.state.position.x,
            target.z + 0.5 - anchor.state.position.z,
        )
        val rise = target.y - anchor.stance.y
        return distance < JumpArcProbe.maxReach(entrySpeed, rise) * OVERSHOOT_REACH_FRACTION
    }

    private fun canReach(anchor: ValueAnchor, target: Stance, sprint: Boolean): Boolean {
        val entrySpeed = maxOf(anchor.speed, if (sprint) SPRINT_TOP_SPEED else WALK_TOP_SPEED)
        val distance = hypot(
            target.x + 0.5 - anchor.state.position.x,
            target.z + 0.5 - anchor.state.position.z,
        )
        val rise = target.y - anchor.stance.y
        return distance <= JumpArcProbe.maxReach(entrySpeed, rise) + REACH_SLACK_BLOCKS
    }

    /**
     * The committed straight run, plus fanned offsets only where they can help.
     *
     * Splitting these apart is the difference between a tape that holds its line on
     * open ground and one that weaves across it.
     */
    private fun actionsForOffsets(
        anchor: ValueAnchor,
        sprint: Boolean,
        target: Stance,
        bearing: Double,
        into: MutableList<TrajectoryDecision>,
    ) {
        into += TrajectoryDecision.Heading(sprint, target, bearing, 0.0, delayFrames = null)
        if (anchor.hazardFrame == null && !turnsAhead(anchor, target)) return
        for (offset in searchConfig.headingFanDegrees) {
            if (offset == 0.0) continue
            into += TrajectoryDecision.Heading(sprint, target, bearing + offset, offset, delayFrames = null)
        }
    }

    private fun launchDelays(anchor: ValueAnchor, edge: CoarseEdge): List<Int> {
        val hint = edge.jumpHint ?: return searchConfig.launchDelays.sortedDescending()
        val from = edge.from.center()
        val to = edge.to.center()
        val along = alongEdge(from, to, anchor.state.position.x, anchor.state.position.z)
        val perTick = alongEdge(
            from, to,
            from.x + anchor.state.velocity.x,
            from.z + anchor.state.velocity.z,
        )
        // Ordered by the hint's geometry, but never narrowed to it. Keeping only the
        // two or three delays the arithmetic likes measured far worse (126 -> 192
        // excess ticks on the corpus): the hint is built for an idealised entry, and
        // the body that actually arrives -- different speed, different yaw, half a
        // block off -- often needs a delay the arithmetic ranks poorly. The geometry
        // is a good guess, and the search dives on the first action, so a good guess
        // first is most of the value. The rest have to stay reachable.
        return searchConfig.launchDelays.sortedBy { delay ->
            abs(along + delay * perTick - hint.launchOffsetBlocks)
        }
    }

    private companion object {

        private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

        /** Walk equilibrium (b/t); the slower of the two measured launch families. */
        private const val WALK_TOP_SPEED = 0.2159

        /**
         * Reach headroom before a launch is refused without simulating it. The model is
         * measured from stance centres and the body may launch from anywhere in its block, so
         * this covers that offset and keeps the filter on the permissive side of any error.
         */
        private const val REACH_SLACK_BLOCKS = 0.75

        /** Grounded ticks after the anchor at which an off-axis launch may fire. */
        private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

        /** Shedding options where an arc would overshoot; one released tick sheds ~45%. */
        private val BRAKE_TICKS = listOf(0, 1, 2)
        private val NO_BRAKE = listOf(0)

        /** Below this share of maximum reach, a full-speed arc flies past the pad. */
        private const val OVERSHOOT_REACH_FRACTION = 0.75

        /**
         * Facings the body may carry while still travelling along the descent bearing.
         *
         * A strafe key moves travel 45 degrees off the facing, so facing 45 degrees the other
         * way puts travel back on the bearing. The pair is the manoeuvre, not either half.
         */
        private val DECOUPLED_FACINGS = listOf(
            MovementKeys.FORWARD_RIGHT to -45.0,
            MovementKeys.FORWARD_LEFT to 45.0,
        )

        /** Heading change over the next two steps that makes a pre-aimed facing worth trying. */
        private const val DECOUPLE_TURN_DEGREES = 35.0

        /** Air-control choices during a launch: hold the line, or drift either way. */
        private val AIRBORNE_KEYS = listOf(
            MovementKeys.FORWARD, MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT,
        )

        /** Stances ahead the descent bearing is aimed at, so the fan spreads around the line. */
        private const val BEARING_LOOKAHEAD = 2

        /** Coarse steps that also get a launch lattice; the rest are reached through anchors. */
        private const val LAUNCH_STEPS = 2
    }
}
