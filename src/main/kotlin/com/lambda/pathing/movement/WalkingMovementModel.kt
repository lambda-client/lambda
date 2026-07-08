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
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * First production integration movement model: conservative flat walking.
 *
 * This deliberately avoids jumps, drops, breaking, and placing. It gives the
 * manager something real to plan through while keeping asymmetric action design
 * out of the initial integration path.
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

    private const val CARDINAL_COST = 1.0
    private val DIAGONAL_COST = sqrt(2.0)

    // Step-up costs more than flat to discourage unnecessary stairs; jumping
    // takes ~10 ticks to clear a block while flat sprint covers it in ~7.
    private const val STEP_UP_COST = 1.5

    // Step-down is slightly costlier than flat to prefer level paths when both
    // exist (avoids the bot spamming small drops just to skim a corner).
    private const val STEP_DOWN_COST = 1.05

    // Gap jump costs — more expensive than walking to avoid pointless jumping.
    private const val JUMP_COST = 2.2
    private const val JUMP_UP_COST = 2.5

    // Fall time is terminal-velocity bound, so deeper drops pay less per block.
    private const val DROP_COST_PER_BLOCK = 0.35

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
        var horizontal = CARDINAL_COST / 1.0
        if (config.allowDiagonal) horizontal = minOf(horizontal, DIAGONAL_COST / sqrt(2.0))
        var ascended = Double.POSITIVE_INFINITY
        var descended = Double.POSITIVE_INFINITY
        if (config.allowVertical) {
            ascended = minOf(ascended, STEP_UP_COST / 1.0)
            descended = minOf(descended, STEP_DOWN_COST / 1.0)
            for (depth in 2..config.maxDropHeight) {
                descended = minOf(descended, dropCost(depth) / depth)
            }
        }
        if (config.allowJump && config.allowVertical) {
            horizontal = minOf(horizontal, JUMP_COST / 2.0)
            ascended = minOf(ascended, JUMP_UP_COST / 1.0)
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
    private fun SafeContext.evaluateMove(
        origin: BlockPos,
        dx: Int,
        dy: Int,
        dz: Int,
        gap: Boolean,
        config: PlannerConfig,
    ): Double? {
        val target = BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)
        if (!isTraversable(target)) return null

        if (gap) {
            // Gap jump (2 forward, same y or +1): needs launch headroom and a
            // clear arc over the gap column (feet + head; +1y variant also
            // needs the apex column above the landing height).
            if (!config.allowJump || !config.allowVertical || !hasHeadClearance(origin)) return null
            val gapBlock = BlockPos(origin.x + dx / 2, origin.y, origin.z + dz / 2)
            if (!isPassableColumnSlice(gapBlock)) return null
            if (!isPassableColumnSlice(BlockPos(gapBlock.x, gapBlock.y + 1, gapBlock.z))) return null
            return when (dy) {
                0 -> JUMP_COST
                1 -> {
                    val apex = BlockPos(gapBlock.x, gapBlock.y + 1, gapBlock.z)
                    if (isPassableColumnSlice(apex) && isPassableColumnSlice(BlockPos(apex.x, apex.y + 1, apex.z))) JUMP_UP_COST else null
                }
                else -> null
            }
        }

        return when (dy) {
            0 -> {
                val diagonal = dx != 0 && dz != 0
                if (!diagonal) {
                    CARDINAL_COST
                } else {
                    if (!config.allowDiagonal) return null
                    val sideA = BlockPos(origin.x + dx, origin.y, origin.z)
                    val sideB = BlockPos(origin.x, origin.y, origin.z + dz)
                    // Fast path: both side blocks are fully traversable (has
                    // clearance AND support). This is the standard case.
                    // Otherwise allow the checkerboard / corner-edge walk: the
                    // side blocks are passable (clearance OK) even if not
                    // fully standable — feet span the diagonal, support only
                    // needs to exist at the destination.
                    if ((isTraversable(sideA) && isTraversable(sideB)) ||
                        (isPassableDiagonalSide(sideA, origin) && isPassableDiagonalSide(sideB, origin))
                    ) DIAGONAL_COST else null
                }
            }

            // Step-up (+1 y, cardinal only): needs jump headroom at the origin.
            1 -> if (config.allowVertical && hasHeadClearance(origin)) STEP_UP_COST else null

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
                var y = origin.y + 1
                while (y > target.y) {
                    if (!isPassableColumnSlice(BlockPos(target.x, y, target.z))) return null
                    y--
                }
                dropCost(depth)
            }
        }
    }

    /**
     * Cost of a walk-off descent of [depth] blocks. Depth 1 keeps its legacy
     * tuned value; deeper drops pay a base plus fall time that grows slower
     * than linearly per block (terminal-velocity-bound), which keeps a real
     * drop cheaper than a long staircase detour of the same height.
     */
    private fun dropCost(depth: Int): Double =
        if (depth <= 1) STEP_DOWN_COST else STEP_DOWN_COST + DROP_COST_PER_BLOCK * depth

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

    fun SafeContext.successors(node: FastVector, config: PlannerConfig): Map<FastVector, Double> {
        val origin = node.toBlockPos()
        if (!isTraversable(origin)) return emptyMap()

        val result = HashMap<FastVector, Double>(16)
        forEachMoveDelta(config) { dx, dy, dz, gap ->
            evaluateMove(origin, dx, dy, dz, gap, config)?.let { cost ->
                result[fastVectorOf(origin.x + dx, origin.y + dy, origin.z + dz)] = cost
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
    fun SafeContext.predecessors(node: FastVector, config: PlannerConfig): Map<FastVector, Double> {
        val target = node.toBlockPos()
        if (!isTraversable(target)) return emptyMap()

        val result = HashMap<FastVector, Double>(16)
        forEachMoveDelta(config) { dx, dy, dz, gap ->
            val origin = BlockPos(target.x - dx, target.y - dy, target.z - dz)
            if (isTraversable(origin)) {
                evaluateMove(origin, dx, dy, dz, gap, config)?.let { cost ->
                    result[fastVectorOf(origin.x, origin.y, origin.z)] = cost
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
    private fun SafeContext.hasHeadClearance(origin: BlockPos): Boolean {
        val aboveHead = BlockPos(origin.x, origin.y + 2, origin.z)
        return isPassableColumnSlice(aboveHead)
    }

    /**
     * Returns true if a 1-block-tall, player-footprint-wide slice centered on
     * [pos] is empty. Used to validate non-feet clearance areas like jump apex
     * or step-down chest height.
     */
    private fun SafeContext.isPassableColumnSlice(pos: BlockPos): Boolean {
        val cx = pos.x + 0.5
        val cz = pos.z + 0.5
        val box = Box(
            cx - PLAYER_HALF_WIDTH, pos.y.toDouble(), cz - PLAYER_HALF_WIDTH,
            cx + PLAYER_HALF_WIDTH, pos.y + 1.0, cz + PLAYER_HALF_WIDTH,
        ).contract(COLLISION_EPSILON)
        return world.isSpaceEmpty(box)
    }

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

    /**
     * Returns true if a diagonal side block is passable: its volume at the
     * player's height is clear and the block below provides a solid surface
     * (even if the block position itself isn't a fully valid standing node).
     *
     * This allows diagonal checkerboard walking where only corner-to-corner
     * blocks exist without their cardinal neighbors.
     */
    private fun SafeContext.isPassableDiagonalSide(sidePos: BlockPos, origin: BlockPos): Boolean {
        val cx = sidePos.x + 0.5
        val cz = sidePos.z + 0.5
        val box = Box(
            cx - PLAYER_HALF_WIDTH, sidePos.y.toDouble(), cz - PLAYER_HALF_WIDTH,
            cx + PLAYER_HALF_WIDTH, sidePos.y + PLAYER_HEIGHT, cz + PLAYER_HALF_WIDTH,
        ).contract(COLLISION_EPSILON)
        return world.isSpaceEmpty(box)
    }

    fun SafeContext.isTraversable(pos: BlockPos): Boolean = hasClearance(Vec3d.ofBottomCenter(pos)) && hasBlockSupport(pos)

    fun SafeContext.isStandingPositionTraversable(pos: Vec3d, horizontalClearanceMargin: Double = 0.0): Boolean =
        hasClearance(pos, horizontalClearanceMargin) && hasContinuousSupport(pos)

    private fun SafeContext.hasClearance(pos: Vec3d, horizontalClearanceMargin: Double = 0.0): Boolean =
        world.isSpaceEmpty(playerBox(pos, horizontalClearanceMargin))

    private fun SafeContext.hasBlockSupport(pos: BlockPos): Boolean {
        val support = pos.down()
        return blockState(support).isSideSolidFullSquare(world, support, Direction.UP)
    }

    private fun SafeContext.hasContinuousSupport(pos: Vec3d): Boolean =
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

    private fun BlockPos.toFastVector() = fastVectorOf(x, y, z)

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
