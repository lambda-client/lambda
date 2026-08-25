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
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.HorizontalPoint
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.MovementKeys
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.movement.bearingBetween
import com.lambda.pathing.movement.center
import kotlin.math.abs

internal class ActionSet(
    private val catalog: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
) {
    fun actions(anchor: ValueAnchor, hazardFrame: Int?): List<TrajectoryDecision> {
        // Within finishing range the anchor gets no expansion vocabulary at all -- the
        // terminal sweep grid in the search owns the last few blocks. This is the guard
        // that used to sit *after* the walk decisions were built, discarding them.
        //
        // The ROOT is exempt: a search may begin inside finishing range (a partial tape
        // legally stops within a few blocks of the goal, and the next leg roots there).
        // With no vocabulary, a single failed finish sweep from the root left the whole
        // search with literally no moves -- "no certified motion from the start state"
        // two walkable blocks from the goal.
        if (anchor.parent != null && field.guide(anchor.stance) <= searchConfig.finishValueTicks) {
            return emptyList()
        }

        val steps = field.steps(
            anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
            anchor.heading(),
        )
        if (steps.isEmpty()) return emptyList()

        val actions = ArrayList<TrajectoryDecision>()
        val walks = ArrayList<TrajectoryDecision>()
        val launches = ArrayList<TrajectoryDecision>()
        val walking = catalog[MovementId.WALK]
        for (step in steps) {
            walking?.let { walks += it.decisions(DecisionContext(anchor, step, config, field.view)) }
        }

        val bearing = descentBearing(anchor, steps.first().to)
        val first = steps.first()
        val target = first.to

        // Only a genuinely ballistic first step is worth leading with. Comparing against
        // the walk id alone made a one-block step down "not walking", so the search tried
        // to *leap* off every staircase before it tried to walk down it.
        val jumpFirst = catalog[first.movement]?.id != MovementId.WALK

        for (sprint in config.sprintModes) {
            actionsForOffsets(anchor, sprint, target, bearing, walks)

            if (turnsAhead(anchor, steps.first().to)) {
                for ((keys, facingOffset) in DECOUPLED_FACINGS) {
                    walks += TrajectoryDecision.Heading(
                        sprint, target, bearing + facingOffset, delayFrames = null, keys = keys,
                    )
                }
            }
        }

        // Blind airborne guesses, and they stay first among the launches. They hold a
        // straight bearing where the solved decisions steer along the stance chain, and
        // demoting them behind the solved ones cost 2700 degrees of turning across the
        // corpus for thirteen collisions -- a bad trade.
        //
        // The *delayed* variants only exist to leave a lip later than a failing walk did,
        // so they are gated on a hazard having actually been seen. Ungated they were the
        // single largest attempt pool in the search (28k of 48k rollouts on the corpus,
        // ~73% rejected after ~12 simulated frames each); gating them dropped attempts
        // by a quarter to a third on the heavy bedrock cases with identical arrivals,
        // marginally fewer total frames, and fewer collisions. Removing delays outright
        // (a {0,3} set) instead broke bedrock-traverse -- the set itself is load-bearing,
        // the unconditional enumeration was not.
        for (sprint in config.sprintModes) {
            for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                if (delay != 0 && anchor.hazardFrame == null) continue
                launches += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = delay)
            }
            if (anchor.hazardFrame != null || turnsAhead(anchor, target)) {
                for (offset in searchConfig.headingFanDegrees) {
                    if (offset == 0.0) continue
                    for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing + offset, delayFrames = delay,
                        )
                    }
                }
            }

            if (hazardFrame != null) {
                for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                    for (air in AIRBORNE_KEYS.drop(1)) {
                        launches += TrajectoryDecision.Heading(
                            sprint, target, bearing, delayFrames = delay,
                            keys = MovementKeys.FORWARD, airborneKeys = air,
                        )
                    }
                }
            }
        }

        // Solved descents are the one thing that must beat the blind hops. A narrow tread
        // has no room to overshoot onto, so a walk that carries speed off it fails and the
        // search falls straight through to a hop -- which leaves the ground and clears two
        // treads instead of one. A drop knows the speed that lands, so it goes first.
        // Everything else keeps its place: promoting *all* solved decisions ahead of the
        // hops cost 2700 degrees of turning across the corpus, because the hops are what
        // hold a straight bearing where the solved ones steer along the stance chain.
        val descents = ArrayList<TrajectoryDecision>()
        for (step in steps.take(LAUNCH_STEPS)) {
            val decisions = movementDecisions(anchor, step)
            if (step.to.y < anchor.stance.y) descents += decisions else launches += decisions
        }

        actions += if (jumpFirst) descents + launches + walks else walks + descents + launches
        return actions
    }

    private fun turnsAhead(anchor: ValueAnchor, firstStep: Stance): Boolean {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD + 1, anchor.heading())
        if (chain.size < 3) return false
        val first = bearingBetween(chain[0].center(), chain[1].center())
        val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
        return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
    }

    private fun descentBearing(anchor: ValueAnchor, firstStep: Stance): Double {
        val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD, anchor.heading())
        val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
        return bearingBetween(
            HorizontalPoint(anchor.state.position.x, anchor.state.position.y, anchor.state.position.z),
            aim,
        )
    }

    /**
     * The straight-ahead heading, plus a fan around it when the body is in trouble.
     *
     * Steering along the stance chain is enough on open ground. It stops being enough at a
     * corner, or once a walk has been seen to fail, and the fan is how the search finds a
     * line the grid could not describe.
     */
    private fun actionsForOffsets(
        anchor: ValueAnchor,
        sprint: Boolean,
        target: Stance,
        bearing: Double,
        into: MutableList<TrajectoryDecision>,
    ) {
        into += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = null)
        if (anchor.hazardFrame == null && !turnsAhead(anchor, target)) return
        for (offset in searchConfig.headingFanDegrees) {
            if (offset == 0.0) continue
            into += TrajectoryDecision.Heading(sprint, target, bearing + offset, delayFrames = null)
        }
    }

    /**
     * Controls for one candidate step, from the movement that owns it.
     *
     * This is the dispatch that makes the vocabulary open. The search no longer knows what
     * a jump or a drop is -- it knows that an edge came from a movement, and that the
     * movement can say what is worth simulating from here.
     */
    private fun movementDecisions(anchor: ValueAnchor, edge: CoarseEdge): List<TrajectoryDecision> {
        val owner = catalog[edge.movement]
        val context = DecisionContext(anchor, edge, config, field.view)
        return buildList {
            // The walking family's vocabulary is generated once per anchor above, not per
            // edge, so only a non-walking owner contributes here.
            if (owner != null && owner.id != MovementId.WALK) addAll(owner.decisions(context))
            catalog.movements.forEach { movement ->
                if (movement !== owner && movement.offersFor(edge)) addAll(movement.decisions(context))
            }
        }
    }

    private companion object {


        private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

        private val DECOUPLED_FACINGS = listOf(
            MovementKeys.FORWARD_RIGHT to -45.0,
            MovementKeys.FORWARD_LEFT to 45.0,
        )

        private const val DECOUPLE_TURN_DEGREES = 35.0

        private val AIRBORNE_KEYS = listOf(
            MovementKeys.FORWARD, MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT,
        )

        private const val BEARING_LOOKAHEAD = 2

        private const val LAUNCH_STEPS = 2
    }
}
