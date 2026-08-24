/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.launch

import com.lambda.util.player.prediction.PlayerPhysicsProfile

/**
 * Which impulse and gait a ballistic arc leaves the ground with.
 *
 * A drop is not a jump with the impulse set to zero by accident: it is the primitive
 * for descending a ledge, and the whole point of naming it is that the arc solver, the
 * coarse cost model and the control program all agree that no jump key is pressed.
 */
enum class LaunchMode(val sprint: Boolean, val jumps: Boolean) {
    SPRINT_JUMP(sprint = true, jumps = true),
    WALK_JUMP(sprint = false, jumps = true),
    SPRINT_DROP(sprint = true, jumps = false),
    WALK_DROP(sprint = false, jumps = false),
    ;

    val drops: Boolean get() = !jumps

    /**
     * Whether this mode can be asked about a landing [rise] blocks away vertically.
     *
     * A jump cannot reach higher than its apex, and a drop that does not descend is not a
     * drop -- it is a walk, and offering it as a launch produces a one-tick "arc" that
     * means nothing. Filtering here keeps both nonsenses out of the solver's ranking.
     */
    fun supports(rise: Double): Boolean = if (jumps) rise <= MAX_JUMP_RISE else rise < 0.0

    companion object {
        /** Vanilla apex is ~1.2522 blocks, so one block up is the ceiling. */
        const val MAX_JUMP_RISE = 1
    }
}

/**
 * One unobstructed arc, walked with the simulator's own recurrences.
 *
 * [heights] is the feet height above the take-off surface at the end of each tick, which
 * is what a swept-box obstacle check needs; [distance] is how far the body travels
 * horizontally before the tick on which it touches down.
 */
class ArcSample(
    val airTicks: Int,
    val distance: Double,
    val heights: DoubleArray,
    /** Cumulative horizontal distance at the end of each tick, parallel to [heights]. */
    val distances: DoubleArray,
    /** Stored horizontal velocity once the landing tick's drag has been applied. */
    val exitSpeed: Double,
) {
    val apex: Double get() = heights.maxOrNull() ?: 0.0
}

/**
 * The scalar form of [com.lambda.util.player.prediction.MovementSimulator]'s airborne
 * motion, along the direction the body is facing.
 *
 * This exists so a launch can be *solved* rather than guessed. Two properties of the
 * vanilla equations make that possible, and both are worth stating because the whole
 * solver rests on them:
 *
 * - Vertical motion does not depend on horizontal speed at all, so the number of air
 *   ticks for a given rise is a constant, not something to search over.
 * - Holding forward on-heading, horizontal velocity evolves by `v' = f * (v + a)`, which
 *   is affine. A sum of affine terms is affine, so the distance covered is an exactly
 *   invertible function of the take-off speed: `d(v) = d(0) + slope * v`.
 *
 * Together those replace the hand-fitted reach table this used to carry with arithmetic
 * that generalises to any rise, any drop, and any physics profile.
 */
data class BallisticProfile(
    val gravity: Double,
    val jumpVelocity: Double,
    /** Ground acceleration actually added per tick, input damping included. */
    val walkGroundAcceleration: Double,
    val sprintGroundAcceleration: Double,
    val slipperiness: Double,
) {
    private val groundFriction = HORIZONTAL_DRAG * slipperiness

    fun groundAcceleration(sprint: Boolean): Double =
        if (sprint) sprintGroundAcceleration else walkGroundAcceleration

    /**
     * Stored horizontal velocity a sustained gait converges to on this surface.
     *
     * Note this is the *velocity*, not the per-tick displacement: the displacement is
     * `cruiseSpeed + acceleration`, which is where the familiar 0.2806 sprint figure
     * comes from. Mixing the two silently mis-solves every arc, so the solver works in
     * stored velocity throughout and only the caller sees blocks-per-tick.
     */
    fun cruiseSpeed(sprint: Boolean): Double {
        val acceleration = groundAcceleration(sprint)
        return groundFriction * acceleration / (1.0 - groundFriction)
    }

    /**
     * Ticks of running before the body has covered [distance] blocks along the ground.
     *
     * The take-off point is a *place*, and reading the current velocity to guess when the
     * body reaches it answers zero for a body standing still -- which is how a standing
     * parkour jump ends up fired on the tick before the run-up that makes it work. Walking
     * the ground recurrence instead accounts for the acceleration in between.
     */
    fun groundRunUpTicks(
        entrySpeed: Double,
        distance: Double,
        sprint: Boolean,
        maxTicks: Int,
    ): Int {
        if (distance <= 0.0) return 0
        val acceleration = groundAcceleration(sprint)
        var velocity = entrySpeed
        var covered = 0.0
        for (tick in 0 until maxTicks) {
            val step = velocity + acceleration
            covered += step
            velocity = step * groundFriction
            if (covered >= distance) return tick + 1
        }
        return maxTicks
    }

    /**
     * Walks one arc from a stance surface to a landing [rise] blocks away vertically.
     *
     * The take-off tick is simulated as vanilla runs it: the jump impulse and the sprint
     * boost are applied first, the tick still accelerates and drags with *ground*
     * values because `travel` reads `onGround` from the previous tick, and only then is
     * the body airborne. Getting that one tick wrong is worth several tenths of a block
     * over a long jump.
     *
     * [holdForward] is the control policy the arc assumes, and for a drop it is a real
     * choice rather than a detail. Holding forward is the longest the body can fly; letting
     * go is the shortest. A two-block step down onto the adjacent pad is only reachable by
     * coasting -- held forward, the body flies past the pad it was aimed at -- so modelling
     * only the held case makes the planner refuse descents it can plainly make.
     *
     * Returns null when the body never comes back down to [rise] within [maxTicks],
     * which for a rising target means the arc simply does not get that high.
     */
    fun fly(
        mode: LaunchMode,
        entrySpeed: Double,
        rise: Double,
        holdForward: Boolean = true,
        maxTicks: Int = MAX_ARC_TICKS,
    ): ArcSample? {
        var velocity = entrySpeed
        var verticalVelocity = 0.0
        var height = 0.0
        var distance = 0.0
        val heights = ArrayList<Double>(maxTicks)
        val distances = ArrayList<Double>(maxTicks)

        if (mode.jumps) {
            verticalVelocity = jumpVelocity
            if (mode.sprint) velocity += SPRINT_JUMP_BOOST
        }

        // Take-off tick: ground acceleration, ground friction, and -- for a jump -- the
        // whole impulse spent as vertical displacement before gravity touches it.
        if (holdForward) velocity += groundAcceleration(mode.sprint)
        distance += velocity
        height += verticalVelocity
        verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
        velocity *= groundFriction
        heights += height
        distances += distance

        val airAcceleration = when {
            !holdForward -> 0.0
            mode.sprint -> SPRINT_AIR_ACCELERATION
            else -> WALK_AIR_ACCELERATION
        }
        for (tick in 1..maxTicks) {
            velocity += airAcceleration
            distance += velocity

            if (verticalVelocity < 0.0) {
                // Descending from below the landing surface: the arc never got high
                // enough, and no amount of horizontal speed fixes that.
                if (height < rise) return null

                // Touchdown is a vertical collision: the fall is clamped to the surface
                // while the tick's horizontal movement still happens in full, because
                // collision resolution is per axis.
                if (height + verticalVelocity <= rise) {
                    heights += rise
                    distances += distance
                    return ArcSample(
                        airTicks = tick,
                        distance = distance,
                        heights = heights.toDoubleArray(),
                        distances = distances.toDoubleArray(),
                        exitSpeed = velocity * HORIZONTAL_DRAG,
                    )
                }
            }

            height += verticalVelocity
            verticalVelocity = (verticalVelocity - gravity) * VERTICAL_DRAG
            velocity *= HORIZONTAL_DRAG
            heights += height
            distances += distance
        }
        return null
    }

    companion object {
        /** @see com.lambda.util.player.prediction.MovementSimulator.jump */
        const val SPRINT_JUMP_BOOST = 0.2

        /** Vanilla damps a full movement input to 0.98 before it becomes velocity. */
        const val INPUT_DAMPING = 0.98

        const val VERTICAL_DRAG = 0.98
        const val HORIZONTAL_DRAG = 0.91

        const val WALK_AIR_ACCELERATION = INPUT_DAMPING * 0.02
        const val SPRINT_AIR_ACCELERATION = INPUT_DAMPING * 0.026

        const val MAX_ARC_TICKS = 24

        private const val DEFAULT_MOVEMENT_SPEED = 0.1
        private const val DEFAULT_SLIPPERINESS = 0.6

        /**
         * An unmodified player on ordinary blocks.
         *
         * The coarse layer builds its templates before any player is in hand, so it masks
         * with these. That is not a regression from what it did before -- the constants it
         * replaced were vanilla defaults inlined with no way to vary them -- and the
         * trajectory layer, which does have a profile, re-solves with [of].
         */
        val VANILLA = of(
            movementSpeed = DEFAULT_MOVEMENT_SPEED,
            gravity = 0.08,
            jumpStrength = 0.42,
            jumpBoostVelocityModifier = 0.0,
            slipperiness = DEFAULT_SLIPPERINESS,
        )

        fun of(
            profile: PlayerPhysicsProfile,
            slipperiness: Double = DEFAULT_SLIPPERINESS,
        ): BallisticProfile = of(
            movementSpeed = profile.movementSpeed,
            gravity = profile.gravity,
            jumpStrength = profile.jumpStrength,
            jumpBoostVelocityModifier = profile.jumpBoostVelocityModifier,
            slipperiness = slipperiness,
        )

        fun of(
            movementSpeed: Double,
            gravity: Double,
            jumpStrength: Double,
            jumpBoostVelocityModifier: Double,
            slipperiness: Double,
        ): BallisticProfile {
            // LivingEntity.applyMovementInput scales the attribute by 0.216/slipperiness^3,
            // which is exactly 1 on ordinary ground and is what makes ice fast.
            val slipperinessCubed = slipperiness * slipperiness * slipperiness
            val walk = movementSpeed * (0.21600002 / slipperinessCubed)
            val sprint = walk * PlayerPhysicsProfile.SPRINT_SPEED_MULTIPLIER
            return BallisticProfile(
                gravity = gravity,
                jumpVelocity = jumpStrength + jumpBoostVelocityModifier,
                walkGroundAcceleration = INPUT_DAMPING * walk,
                sprintGroundAcceleration = INPUT_DAMPING * sprint,
                slipperiness = slipperiness,
            )
        }
    }
}
