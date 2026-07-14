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

package com.lambda.pathing.movement

import com.lambda.context.SafeContext
import com.lambda.config.blocks.PlannerConfig
import com.lambda.pathing.primitives.MoveCosts
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.WorldView
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * First production integration movement model: conservative flat walking.
 *
 * This deliberately avoids jumps, drops, breaking, and placing. It gives the
 * manager something real to plan through while keeping asymmetric action design
 * out of the initial integration path.
 *
 * Plan-time queries (successor/predecessor generation, node traversability)
 * read a [WorldView] — precomputed traits over int-encoded blockstates, never
 * live Minecraft collision shapes (WP1). Execution-time checks at continuous
 * positions stay on the live world via [SafeContext]: the executor's job is to
 * monitor reality, not the planner's model of it.
 */
object WalkingMovementModel {
    // Pairs of (dx, dz) flattened — cardinal first, then diagonals.
    private val cardinalOffsets = intArrayOf(
        -1, 0,
        1, 0,
        0, -1,
        0, 1,
    )

    private val cardinalAndDiagonalOffsets = intArrayOf(
        -1, 0,
        1, 0,
        0, -1,
        0, 1,
        -1, -1,
        -1, 1,
        1, -1,
        1, 1,
    )

    /**
     * Admissibility caps for the planner's anisotropic heuristic: the minimum
     * cost any *enabled* move pays per block of displacement along each axis
     * class. Derived from the move table itself (not hand-written) so adding
     * a cheaper move type can never silently make the heuristic overestimate
     * — the exact failure mode theory note T1 documents.
     */
    data class HeuristicCaps(
        val minCostPerHorizontalBlock: Double,
        val minCostPerAscendedBlock: Double,
        val minCostPerDescendedBlock: Double,
    )

    fun heuristicCaps(config: PlannerConfig): HeuristicCaps {
        var horizontal = MoveCosts.CARDINAL / 1.0
        if (config.allowDiagonal) horizontal = minOf(horizontal, MoveCosts.DIAGONAL / sqrt(2.0))
        var ascended = Double.POSITIVE_INFINITY
        var descended = Double.POSITIVE_INFINITY
        if (config.allowVertical) {
            ascended = minOf(ascended, MoveCosts.STEP_UP / 1.0)
            descended = minOf(descended, MoveCosts.STEP_DOWN / 1.0)
            for (depth in 2..config.maxDropHeight) {
                descended = minOf(descended, MoveCosts.drop(depth) / depth)
            }
        }
        if (config.allowJump && config.allowVertical) {
            horizontal = minOf(horizontal, MoveCosts.JUMP / 2.0)
            ascended = minOf(ascended, MoveCosts.JUMP_UP / 1.0)
        }
        return HeuristicCaps(horizontal, ascended, descended)
    }

    /**
     * The forward move set, evaluated origin-relative. Both [successors] and
     * [predecessors] enumerate this same table — successors by applying the
     * delta to the origin, predecessors by *subtracting* it from the target
     * and validating the identical forward conditions. Sharing one validity
     * check per move type is what makes Succ/Pred provably mirror images:
     * a move exists backward iff it exists forward, evaluated at its true
     * origin. (This is the primitive-template inverse idea from the research
     * plan, specialized to the walking model — and it fixes the phantom
     * step-up edges the symmetric-predecessor assumption used to create,
     * e.g. mirroring a legal step-down into an illegal jump under a low
     * ceiling.)
     */
    private fun evaluateMove(
        view: WorldView,
        ox: Int,
        oy: Int,
        oz: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        gap: Boolean,
        config: PlannerConfig,
    ): Double? {
        val tx = ox + dx
        val ty = oy + dy
        val tz = oz + dz
        if (!isTraversable(view, tx, ty, tz)) return null

        if (gap) {
            // Gap jump (2 forward, same y or +1): needs launch headroom and a
            // clear arc over the gap column (feet + head; +1y variant also
            // needs the apex column above the landing height).
            if (!config.allowJump || !config.allowVertical || !hasHeadClearance(view, ox, oy, oz)) return null
            val gx = ox + dx / 2
            val gz = oz + dz / 2
            if (!isSlicePassable(view, gx, oy, gz)) return null
            if (!isSlicePassable(view, gx, oy + 1, gz)) return null
            // The APEX slice over the gap column is not optional for a flat
            // jump either. By the time the arc crosses the mid column the feet
            // are ~1 block above the takeoff, so the body occupies oy+1 AND
            // oy+2 there — a block at oy+2 is a head bonk that flattens the
            // arc and drops the player into the gap. Checking only oy/oy+1 let
            // the graph emit jump edges whose arc passes straight through a
            // block; the executor's flight sim then (correctly) refused to
            // launch and the agent stalled at the lip. Both this oracle and
            // MoveTable shared the omission, which is why the differential
            // test held them equal and never caught it.
            if (!isSlicePassable(view, gx, oy + 2, gz)) return null
            return when (dy) {
                0 -> MoveCosts.JUMP
                1 -> MoveCosts.JUMP_UP
                else -> null
            }
        }

        return when (dy) {
            0 -> {
                val diagonal = dx != 0 && dz != 0
                if (!diagonal) {
                    MoveCosts.CARDINAL
                } else {
                    if (!config.allowDiagonal) return null
                    // Fast path: both side blocks are fully traversable (has
                    // clearance AND support). This is the standard case.
                    // Otherwise allow the checkerboard / corner-edge walk: the
                    // side blocks are passable (clearance OK) even if not
                    // fully standable — feet span the diagonal, support only
                    // needs to exist at the destination.
                    if ((isTraversable(view, ox + dx, oy, oz) && isTraversable(view, ox, oy, oz + dz)) ||
                        (isPassableDiagonalSide(view, ox + dx, oy, oz) && isPassableDiagonalSide(view, ox, oy, oz + dz))
                    ) MoveCosts.DIAGONAL else null
                }
            }

            // Step-up (+1 y, cardinal only): needs jump headroom at the origin.
            1 -> if (config.allowVertical && hasHeadClearance(view, ox, oy, oz)) MoveCosts.STEP_UP else null

            // Step-down / drop (-1..-maxDropHeight y, cardinal only): walking
            // off a ledge and falling h blocks. Asymmetric — the reverse move
            // does not exist, which is exactly why the planner needs the true
            // predecessor enumeration.
            //
            // Clearance: while stepping off, the player briefly occupies the
            // target column at origin height (slices origin.y and origin.y+1
            // — the +1 slice matters because the head tops out at +1.8), and
            // then falls through every slice of the target column down to the
            // landing feet level.
            else -> {
                if (dy > 0 || !config.allowVertical) return null
                val depth = -dy
                if (depth > config.maxDropHeight && depth > 1) return null
                var y = oy + 1
                while (y > ty) {
                    if (!isSlicePassable(view, tx, y, tz)) return null
                    y--
                }
                MoveCosts.drop(depth)
            }
        }
    }



    /**
     * Iterates every enabled move delta as (dx, dy, dz, gap) and invokes
     * [emit]. One enumeration shared by both search directions.
     */
    private inline fun forEachMoveDelta(config: PlannerConfig, emit: (dx: Int, dy: Int, dz: Int, gap: Boolean) -> Unit) {
        val offsets = if (config.allowDiagonal) cardinalAndDiagonalOffsets else cardinalOffsets
        var i = 0
        while (i < offsets.size) {
            val dx = offsets[i]
            val dz = offsets[i + 1]
            i += 2
            val diagonal = dx != 0 && dz != 0

            emit(dx, 0, dz, false)

            // Vertical moves: cardinal directions only for now. Diagonal step
            // up/down has fiddly corner-clearance rules and the executor's
            // any-angle controller doesn't yet steer mid-segment rises.
            if (diagonal || !config.allowVertical) continue
            emit(dx, 1, dz, false)
            emit(dx, -1, dz, false)
            for (depth in 2..config.maxDropHeight) {
                emit(dx, -depth, dz, false)
            }

            if (config.allowJump) {
                emit(dx * 2, 0, dz * 2, true)
                emit(dx * 2, 1, dz * 2, true)
            }
        }
    }

    fun successors(view: WorldView, node: FastVector, config: PlannerConfig): Map<FastVector, Double> {
        val ox = node.x
        val oy = node.y
        val oz = node.z
        if (!isTraversable(view, ox, oy, oz)) return emptyMap()

        val result = HashMap<FastVector, Double>(16)
        forEachMoveDelta(config) { dx, dy, dz, gap ->
            evaluateMove(view, ox, oy, oz, dx, dy, dz, gap, config)?.let { cost ->
                result[fastVectorOf(ox + dx, oy + dy, oz + dz)] = cost
            }
        }
        return result
    }

    /**
     * True incoming edges of [node]: every origin that has a forward move
     * landing exactly on [node], validated with the forward conditions at
     * that origin. Required by D* Lite's backward expansion — using
     * successors as predecessors silently assumes every move is reversible,
     * which drops (down-only ledges) and headroom-gated jumps are not.
     */
    fun predecessors(view: WorldView, node: FastVector, config: PlannerConfig): Map<FastVector, Double> {
        val tx = node.x
        val ty = node.y
        val tz = node.z
        if (!isTraversable(view, tx, ty, tz)) return emptyMap()

        val result = HashMap<FastVector, Double>(16)
        forEachMoveDelta(config) { dx, dy, dz, gap ->
            val ox = tx - dx
            val oy = ty - dy
            val oz = tz - dz
            if (isTraversable(view, ox, oy, oz)) {
                evaluateMove(view, ox, oy, oz, dx, dy, dz, gap, config)?.let { cost ->
                    result[fastVectorOf(ox, oy, oz)] = cost
                }
            }
        }
        return result
    }

    /**
     * Conservative node invalidation around a changed block: every node whose
     * outgoing edges could read this block. Extents are derived from the move
     * table so they stay correct as moves are added: horizontally the longest
     * move reaches 2 (gap jump); downward a change up to (maxDropHeight + 1)
     * below a node can be its drop landing's support; upward a change 2 above
     * can be its jump headroom.
     */
    fun affectedNodes(changedBlock: BlockPos, config: PlannerConfig): Set<FastVector> = buildSet {
        val up = 2 + config.maxDropHeight.coerceAtLeast(1)
        for (dx in -2..2) {
            for (dy in -2..up) {
                for (dz in -2..2) {
                    add(fastVectorOf(changedBlock.x + dx, changedBlock.y + dy, changedBlock.z + dz))
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Plan-time predicates over WorldView traits.
    // ------------------------------------------------------------------

    /**
     * A player-footprint slice of this voxel is unobstructed: the center
     * column of the voxel itself is clear and the block below does not poke
     * above its own voxel top into this one (fences, walls: 1.5 tall).
     */
    private fun isSlicePassable(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        view.traits(x, y, z).centerPassable && !view.traits(x, y - 1, z).intrudesAbove

    /**
     * A node the player can stand on: full-square support below, feet slice
     * unobstructed, head voxel's center column clear.
     */
    fun isTraversable(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        view.traits(x, y - 1, z).standableFullTop &&
            isSlicePassable(view, x, y, z) &&
            view.traits(x, y + 1, z).centerPassable

    fun isTraversable(view: WorldView, pos: BlockPos): Boolean = isTraversable(view, pos.x, pos.y, pos.z)

    /**
     * Checks that the block above the head (i.e. y+2 relative to feet) leaves
     * room for the jump apex without bonking. Required before allowing a
     * step-up from this position.
     *
     * The +2 slice is what matters: standing only needs feet (+0) and head
     * (+1) clear, but a jump raises the head into the +2 slice. Checking +1
     * here (a past bug) generated step-up edges under 2-high ceilings where
     * the jump is physically impossible — the executor then bonked and
     * retried forever.
     */
    private fun hasHeadClearance(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        isSlicePassable(view, x, y + 2, z)

    /**
     * Returns true if a diagonal side block is passable: its volume at the
     * player's height is clear even if the block position itself isn't a
     * fully valid standing node (no support required).
     *
     * This allows diagonal checkerboard walking where only corner-to-corner
     * blocks exist without their cardinal neighbors.
     */
    private fun isPassableDiagonalSide(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        isSlicePassable(view, x, y, z) && view.traits(x, y + 1, z).centerPassable

    // ------------------------------------------------------------------
    // Execution-time checks at continuous positions — live world on
    // purpose: the executor monitors reality, not the planner's model.
    // ------------------------------------------------------------------

    /**
     * Execution-time headroom check for issuing a step-up jump from an
     * arbitrary continuous position. Plan-time clearance ([hasHeadClearance])
     * is validated at node centers only — the player usually stands somewhere
     * between nodes when the executor wants to jump, and the ceiling there was
     * never part of any plan check.
     *
     * Deliberately requires [STEP_UP_HEADROOM] of rise rather than the full
     * free-flight apex ([JUMP_APEX_RISE]): a ceiling that clips the top of the
     * arc but still allows a one-block rise (tunnel staircases with 3-block
     * ceilings) must not block the jump.
     */
    fun SafeContext.hasJumpApexClearance(feetPos: Vec3d): Boolean {
        val box = Box(
            feetPos.x - PLAYER_HALF_WIDTH, feetPos.y + PLAYER_HEIGHT, feetPos.z - PLAYER_HALF_WIDTH,
            feetPos.x + PLAYER_HALF_WIDTH, feetPos.y + STEP_UP_HEADROOM + PLAYER_HEIGHT, feetPos.z + PLAYER_HALF_WIDTH,
        ).contract(COLLISION_EPSILON)
        return world.isSpaceEmpty(box)
    }

    fun SafeContext.isStandingPositionTraversable(pos: Vec3d, horizontalClearanceMargin: Double = 0.0): Boolean =
        hasClearance(pos, horizontalClearanceMargin) && hasContinuousSupport(pos)

    private fun SafeContext.hasClearance(pos: Vec3d, horizontalClearanceMargin: Double = 0.0): Boolean =
        world.isSpaceEmpty(playerBox(pos, horizontalClearanceMargin))

    /**
     * Execution-time ground probe at a continuous position: true when the
     * inset footprint at [pos] rests on collision. Public for the executor's
     * gap-ahead detection — a flat segment whose midpoint has no support is
     * a gap-jump edge, not a walk.
     */
    fun SafeContext.hasContinuousSupport(pos: Vec3d): Boolean =
        !world.isSpaceEmpty(supportBox(pos))

    private fun playerBox(pos: Vec3d, horizontalClearanceMargin: Double = 0.0): Box {
        val halfWidth = PLAYER_HALF_WIDTH + horizontalClearanceMargin.coerceAtLeast(0.0)
        return Box(pos.x - halfWidth, pos.y, pos.z - halfWidth, pos.x + halfWidth, pos.y + PLAYER_HEIGHT, pos.z + halfWidth)
            .contract(COLLISION_EPSILON)
    }

    private fun supportBox(pos: Vec3d): Box =
        Box(
            pos.x - PLAYER_HALF_WIDTH + SUPPORT_INSET,
            pos.y - SUPPORT_EPSILON,
            pos.z - PLAYER_HALF_WIDTH + SUPPORT_INSET,
            pos.x + PLAYER_HALF_WIDTH - SUPPORT_INSET,
            pos.y + SUPPORT_EPSILON,
            pos.z + PLAYER_HALF_WIDTH - SUPPORT_INSET,
        )

    private const val PLAYER_HALF_WIDTH = 0.3
    private const val PLAYER_HEIGHT = 1.8

    // Vanilla jump apex height (initial vy 0.42 with per-tick drag/gravity
    // tops out at ~1.252 blocks). Nominal — replaced by the WP0 calibration
    // pass once the harness measures it.
    private const val JUMP_APEX_RISE = 1.252

    // Minimum unobstructed rise above the head required to complete a
    // one-block step-up (1.0 needed to land, plus margin). Lower than
    // JUMP_APEX_RISE on purpose — see hasJumpApexClearance.
    private const val STEP_UP_HEADROOM = 1.1
    private const val COLLISION_EPSILON = 1.0E-6
    private const val SUPPORT_EPSILON = 1.0E-6
    private const val SUPPORT_INSET = 0.05
}
