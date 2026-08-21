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

internal class ActionSet(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
) {
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

        val bearing = descentBearing(anchor, steps.first().to)
        val target = steps.first().to
        val jumpFirst = steps.first().kind == CoarseMoveKind.JUMP_CANDIDATE
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

        if (field.guide(anchor.stance) <= searchConfig.finishValueTicks) return actions

        for (sprint in config.sprintModes) {
            for (delay in OFF_AXIS_LAUNCH_DELAYS) {
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

        for (sprint in config.sprintModes) {
            for (step in steps.take(LAUNCH_STEPS)) {
                if (!canReach(anchor, step.to, sprint)) continue
                for (delay in launchDelays(anchor, step)) {
                    launches += TrajectoryDecision.Launch(sprint, step.to, delay)
                }
            }
        }

        actions += if (jumpFirst) launches + walks else walks + launches
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

    private fun canReach(anchor: ValueAnchor, target: Stance, sprint: Boolean): Boolean {
        val entrySpeed = maxOf(anchor.speed, if (sprint) SPRINT_TOP_SPEED else WALK_TOP_SPEED)
        val distance = hypot(
            target.x + 0.5 - anchor.state.position.x,
            target.z + 0.5 - anchor.state.position.z,
        )
        val rise = target.y - anchor.stance.y
        return distance <= JumpArcProbe.maxReach(entrySpeed, rise) + REACH_SLACK_BLOCKS
    }

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

        return searchConfig.launchDelays.sortedBy { delay ->
            abs(along + delay * perTick - hint.launchOffsetBlocks)
        }
    }

    private companion object {
        private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

        private const val WALK_TOP_SPEED = 0.2159

        private const val REACH_SLACK_BLOCKS = 0.75

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
