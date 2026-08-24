/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.launch.LaunchSolution

sealed interface TrajectoryDecision {
    /** Which movement owns this decision, and therefore builds and finishes it. */
    val movement: MovementId

    val sprint: Boolean

    val step: Stance?

    /**
     * How robust this decision is believed to be, for ordering the search's rollouts.
     *
     * Only solved decisions can answer honestly; everything else is neutral and keeps its
     * place in the order it was generated in.
     */
    val margin: Double get() = 0.0

    data class Walk(
        override val sprint: Boolean,
        override val step: Stance?,
        val lookAheadNodes: Int,
        val easeTurns: Boolean,
        override val movement: MovementId = MovementId.WALK,
    ) : TrajectoryDecision

    /**
     * Leave the ground with a jump, [delayFrames] grounded ticks from now.
     *
     * The delay is no longer a blind enumeration: it is the tick on which the body is
     * predicted to reach [solution]'s take-off point, with the search bracketing a frame
     * either side of it.
     */
    data class Launch(
        override val sprint: Boolean,
        override val step: Stance?,
        val delayFrames: Int,
        val solution: LaunchSolution? = null,
        override val movement: MovementId = MovementId.JUMP,
    ) : TrajectoryDecision {
        override val margin: Double get() = solution?.margin ?: 0.0
    }

    /**
     * Descend a ledge with the feet down, arriving at the lip at a solved speed.
     *
     * The missing primitive. Walking off at whatever speed the approach happened to leave
     * overshoots any landing narrower than the body's stopping distance -- a sprint clears
     * 1.26 blocks off a one-block ledge -- and jumping off makes it strictly worse by
     * adding height, air time and fall damage to a move whose purpose is to lose height.
     */
    data class Drop(
        override val sprint: Boolean,
        override val step: Stance?,
        val solution: LaunchSolution,
        override val movement: MovementId = MovementId.DROP,
    ) : TrajectoryDecision {
        override val margin: Double get() = solution.margin
    }

    /**
     * Hold a ladder or vine and move along it.
     *
     * [holdForward] is separate from the direction of travel on purpose. Vanilla only
     * re-asserts a climbing body's rise while it is *horizontally colliding*, so going up
     * means pressing into the hold and going down means letting go -- but stepping onto a
     * ladder, or off it onto a ledge, also needs the key held even though neither changes
     * height. Deriving the press from "is this ascending" left both of those pressing
     * nothing at all.
     */
    data class Climb(
        override val step: Stance?,
        /** Press forward while the feet are still down -- walking into or off a column. */
        val holdForward: Boolean,
        /**
         * Press forward once the body is off the ground and holding the climbable.
         *
         * Separate from [holdForward] because entering a shaft over its lip needs the
         * opposite of leaving one: press to get over the edge, then let go, or the press
         * becomes a horizontal collision and vanilla answers that by sending the body back
         * up the ladder it just stepped into.
         */
        val holdWhileClimbing: Boolean,
        /**
         * Whether the rise is sustained with the jump key.
         *
         * Vanilla re-asserts a climbing body's rise on `horizontalCollision || isJumping`,
         * so jumping climbs a ladder exactly as pressing into it does -- and unlike pressing,
         * it needs nothing to press against. That is the only way up a vine hung with no
         * block behind it, where holding forward would walk the body out of the very hitbox
         * it has to stay inside.
         */
        val climbWithJump: Boolean,
        /** Yaw that keeps the body pressed into the climbable; null to hold the current facing. */
        val yaw: Double?,
        override val sprint: Boolean = false,
        override val movement: MovementId = MovementId.CLIMB,
    ) : TrajectoryDecision

    data class Heading(
        override val sprint: Boolean,
        override val step: Stance?,
        val yaw: Double,
        val delayFrames: Int?,
        val keys: MovementKeys = MovementKeys.FORWARD,
        val airborneKeys: MovementKeys = keys,
        override val movement: MovementId = MovementId.WALK,
    ) : TrajectoryDecision
}
