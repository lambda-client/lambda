/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.horizontalDistance
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.math.Vec3d
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object CoarseMoveRates {
    const val SPRINT_BLOCKS_PER_TICK = 0.2806

    const val SPRINT_JUMP_BLOCKS_PER_TICK = 0.3640

    const val STEP_UP_BLOCKS_PER_TICK = 0.1000

    /**
     * The rise a walking body clears without doing anything about it.
     *
     * Vanilla's step height: below this the feet are lifted by collision resolution, not by
     * a deliberate move, so a half-block riser costs a stride and not a step-up. On terrain
     * made of whole blocks nothing is ever under it and the distinction never arose.
     *
     * @see net.minecraft.entity.attribute.EntityAttributes.STEP_HEIGHT
     */
    const val FREE_STEP_RISE = 0.6

    /**
     * The shortest hop worth calling a jump, in blocks.
     *
     * Anything nearer is an adjacent stance, which walking or a step-up already reaches --
     * and reaches more cheaply than leaving the ground for it.
     */
    const val MIN_JUMP_DISTANCE = 2.0

    const val JUMP_AIR_TICKS = 12.0

    const val JUMP_RISE_TICKS = 2.0

    /**
     * Vanilla clamps a climbing body to 0.117 blocks per tick upward.
     *
     * @see net.minecraft.entity.LivingEntity.applyClimbingSpeed
     */
    const val CLIMB_TICKS_PER_BLOCK = 1.0 / 0.117

    private const val MAX_FALL_TABLE_DEPTH = 12

    private val FALL_TICKS = DoubleArray(MAX_FALL_TABLE_DEPTH + 1).also { table ->
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        while (depth <= MAX_FALL_TABLE_DEPTH) {
            tick++
            velocity = (velocity + 0.08) * 0.98
            fallen += velocity
            while (depth <= MAX_FALL_TABLE_DEPTH && fallen >= depth) {
                table[depth] = tick.toDouble()
                depth++
            }
        }
    }

    fun fallTicks(depth: Int): Double = FALL_TICKS[depth.coerceIn(1, MAX_FALL_TABLE_DEPTH)]
}

data class Stance(val x: Int, val y: Int, val z: Int) {
    fun offset(dx: Int, dy: Int, dz: Int) = Stance(x + dx, y + dy, z + dz)

    override fun toString() = "($x, $y, $z)"

    companion object {
        /**
         * Which stance a body at [position] occupies.
         *
         * A grounded body is standing on a *surface*, and a stance names the cell above
         * whatever provides it -- so the question is which cell holds the body up, not which
         * cell its feet are in. An airborne body is simply inside a cell, and that cell is
         * the answer.
         *
         * On whole blocks a grounded body's feet sit exactly on a cell boundary and the two
         * readings agree, which is why plain flooring served for as long as the graph only
         * knew about cubes. On a carpet the feet are at y.0625 and they do not: flooring
         * names the carpet's own cell, one below the stance the graph built the route from,
         * so the body was never standing where the plan said it started.
         */
        fun of(position: Vec3d, onGround: Boolean): Stance = Stance(
            floor(position.x).toInt(),
            if (onGround) floor(position.y - SURFACE_EPSILON).toInt() + 1
            else floor(position.y + SURFACE_EPSILON).toInt(),
            floor(position.z).toInt(),
        )

        /** Slack for a body resting exactly on a cell boundary, which is the common case. */
        private const val SURFACE_EPSILON = 1e-6
    }
}

@JvmInline
value class MotionTemplateId(val value: Int)

data class CoarseEdgeId(val template: MotionTemplateId, val from: Stance)

data class CoarseEdge(
    val id: CoarseEdgeId,
    val from: Stance,
    val to: Stance,
    val movement: MovementId,
    val lowerBoundTicks: Double,
    val readSet: Set<VoxelPos>,
    /** The solved take-off for a ballistic edge; null for edges that keep their feet down. */
    val launch: LaunchSolution? = null,
)

class CoarseMoveCosts(
    val cardinalWalk: Double,
    val diagonalWalk: Double,
    val stepUp: Double,
    val walkOff: (depth: Int) -> Double,
    /**
     * Priced by how far the jump actually goes, not by how many cells it steps.
     *
     * A span and a separate diagonal variant could only describe the eight compass rays.
     * Distance describes those and every off-axis landing too, and gives the same answer
     * for the cases that used to have their own entry.
     */
    val jumpCandidate: (distance: Double, verticalOffset: Int) -> Double,
    val drop: (span: Int, depth: Int) -> Double = { _, depth -> walkOff(depth) },
    val climb: (blocks: Int) -> Double = { blocks -> blocks * CoarseMoveRates.CLIMB_TICKS_PER_BLOCK },
) {
    init {
        validate("cardinalWalk", cardinalWalk)
        validate("diagonalWalk", diagonalWalk)
        validate("stepUp", stepUp)
    }

    fun jumpCandidateCost(distance: Double, verticalOffset: Int): Double {
        require(distance >= CoarseMoveRates.MIN_JUMP_DISTANCE) {
            "a jump candidate must reach past the adjacent stance: $distance"
        }
        return jumpCandidate(distance, verticalOffset).also {
            validate("jumpCandidate($distance, $verticalOffset)", it)
        }
    }

    fun walkOffCost(depth: Int): Double {
        require(depth > 0) { "walk-off depth must be positive" }
        return walkOff(depth).also { validate("walkOff($depth)", it) }
    }

    fun climbCost(blocks: Int): Double {
        require(blocks > 0) { "a climb must cover at least one block" }
        return climb(blocks).also { validate("climb($blocks)", it) }
    }

    fun dropCost(span: Int, depth: Int): Double {
        require(span >= 1) { "a drop must cross at least the adjacent stance" }
        require(depth > 0) { "drop depth must be positive" }
        return drop(span, depth).also { validate("drop($span, $depth)", it) }
    }

    private fun validate(name: String, value: Double) {
        require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
    }

    companion object {
        /**
         * Price for a (span, rise) pair no launch mode can fly.
         *
         * Templates are priced when the library is built, long before any terrain is in
         * hand, so an impossible combination still needs a finite number. It never reaches
         * the graph: the arc probe refuses to match the template against any origin.
         */
        private const val UNREACHABLE_BALLISTIC_TICKS = 1000.0

        /** Braking to the solved leave speed and recovering on landing. */
        private const val DROP_SETTLE_TICKS = 2.0

        /**
         * Costs taken from the arc equations rather than from tables beside them.
         *
         * Every ballistic price here is the tick count [BallisticProfile] actually
         * produces for that move, so the cost model and the reachability model can no
         * longer disagree -- which they did: the old flat 12-tick floor charged a rising
         * jump 14 ticks for a move that takes 9, and the planner routed around jumps it
         * should have taken.
         */
        fun measured(
            transitionOverheadTicks: Double = 0.0,
            profile: BallisticProfile = BallisticProfile.VANILLA,
        ): CoarseMoveCosts {
            require(transitionOverheadTicks >= 0.0 && transitionOverheadTicks.isFinite()) {
                "transitionOverheadTicks must be non-negative and finite: $transitionOverheadTicks"
            }
            val walk = 1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK
            val step = 1.0 / CoarseMoveRates.STEP_UP_BLOCKS_PER_TICK

            // Air ticks plus the take-off tick itself. Vertical motion ignores horizontal
            // speed, so this depends only on the rise -- span never enters it.
            fun ballistic(jumping: Boolean, rise: Int): Double? = LaunchMode.entries
                .filter { it.jumps == jumping && it.supports(rise.toDouble()) }
                .mapNotNull { mode -> profile.fly(mode, profile.cruiseSpeed(mode.sprint), rise.toDouble())?.airTicks }
                .minOrNull()?.let { (it + 1).toDouble() }

            fun jump(horizontalDistance: Double, verticalOffset: Int): Double =
                max(
                    ballistic(jumping = true, rise = verticalOffset) ?: UNREACHABLE_BALLISTIC_TICKS,
                    horizontalDistance / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK,
                )

            return CoarseMoveCosts(
                cardinalWalk = walk + transitionOverheadTicks,
                diagonalWalk = sqrt(2.0) * walk + transitionOverheadTicks,
                stepUp = step + transitionOverheadTicks,
                walkOff = { depth -> max(walk, CoarseMoveRates.fallTicks(depth)) + 1.0 + transitionOverheadTicks },
                jumpCandidate = { distance, vo -> jump(distance, vo) + transitionOverheadTicks },
                drop = { span, depth ->
                    // The fall and the traverse happen at once, so the move costs whichever
                    // takes longer -- plus the settle, which is the part a walk-off pretends
                    // is free and then pays for by overshooting the pad.
                    max(
                        ballistic(jumping = false, rise = -depth) ?: UNREACHABLE_BALLISTIC_TICKS,
                        span / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK,
                    ) + DROP_SETTLE_TICKS + transitionOverheadTicks
                },
            )
        }
    }
}

data class SimpleMoveOptions(
    val allowDiagonal: Boolean = true,
    val allowStepUp: Boolean = true,
    val maxWalkOffDepth: Int = 3,
    /**
     * Furthest a controlled drop may carry the body horizontally while descending.
     *
     * A span of 1 is the stance directly across; 2 crosses a one-block gap on the way
     * down. Vanilla drop reach is short -- about 1.3 blocks off a one-block ledge -- so
     * there is little point going far past 2.
     */
    val maxDropSpan: Int = 2,
    /**
     * Let the graph route up and down ladders and vines.
     *
     * Off by default, and the reason is worth recording: climb templates are cheap per
     * block, so registering them lowers the admissible ascent bound for *every* search,
     * including ones with no ladder anywhere near them. A weaker bound is a slower search,
     * and on a long route a slower search is one that runs out of expansions before it
     * arrives. Movements are not free just because their terrain is absent.
     */
    val allowClimbing: Boolean = false,
    val allowJumpCandidates: Boolean = true,
    val maxJumpSpan: Int = 4,
    val maxJumpDrop: Int = 1,
    /**
     * Whether landings off the eight compass rays are offered.
     *
     * On terrain built by hand they are most of the interesting gaps -- three across and one
     * to the side has no cardinal or diagonal template. They roughly double the graph's
     * fan-out on open ground, which is the only reason this is a switch rather than simply
     * how the graph works.
     */
    val allowOffAxisJumps: Boolean = true,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxDropSpan >= 1) { "maxDropSpan must reach at least the adjacent stance" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
        require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
    }
}
