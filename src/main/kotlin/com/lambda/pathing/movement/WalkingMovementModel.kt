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

    // Heuristic caps, derived from the cost table above as the minimum cost any
    // edge pays per block of movement along each axis class. The planner's
    // admissible heuristic is built from these, so they must stay true minima:
    // when adding a cheaper edge type, update the corresponding cap (per-axis
    // ratio of the new edge), or the heuristic silently overestimates.
    // Horizontal: cardinal walk (1 cost / 1 block) is beaten by nothing yet —
    // gap jumps pay 2.2 / 2 blocks. Ascent: step-up pays 1.5 / 1 block (jump-up
    // pays 2.5 for 1 up). Descent: step-down pays 1.05 / 1 block.
    const val MIN_COST_PER_HORIZONTAL_BLOCK = 1.0
    const val MIN_COST_PER_ASCENDED_BLOCK = STEP_UP_COST
    const val MIN_COST_PER_DESCENDED_BLOCK = STEP_DOWN_COST

    fun SafeContext.successors(node: FastVector, config: PlannerConfig): Map<FastVector, Double> {
        val origin = node.toBlockPos()
        if (!isTraversable(origin)) return emptyMap()

        val offsets = if (config.allowDiagonal) cardinalAndDiagonalOffsets else cardinalOffsets
        val result = HashMap<FastVector, Double>(offsets.size)

        // Jump head clearance is independent of direction — compute once.
        val canJumpFromHere = config.allowVertical && hasHeadClearance(origin)

        var i = 0
        while (i < offsets.size) {
            val dx = offsets[i]
            val dz = offsets[i + 1]
            i += 2
            val diagonal = dx != 0 && dz != 0

            // ---- Same-y move (flat walk) ----
            val flat = BlockPos(origin.x + dx, origin.y, origin.z + dz)
            if (isTraversable(flat)) {
                if (!diagonal) {
                    result[flat.toFastVector()] = CARDINAL_COST
                } else {
                    val sideA = BlockPos(origin.x + dx, origin.y, origin.z)
                    val sideB = BlockPos(origin.x, origin.y, origin.z + dz)
                    // Fast path: both side blocks are fully traversable (has
                    // clearance AND support). This is the standard case.
                    if (isTraversable(sideA) && isTraversable(sideB)) {
                        result[flat.toFastVector()] = DIAGONAL_COST
                    } else if (isPassableDiagonalSide(sideA, origin) && isPassableDiagonalSide(sideB, origin)) {
                        // Checkerboard / corner-edge walk: the side blocks are
                        // passable (clearance OK) even if not fully standable
                        // (no support). The player walks on the shared corner
                        // edge — feet span the diagonal, support only needs
                        // to exist at the destination.
                        result[flat.toFastVector()] = DIAGONAL_COST
                    }
                }
            }

            // Vertical moves: cardinal directions only for now. Diagonal step
            // up/down has fiddly corner-clearance rules and the executor's
            // any-angle controller doesn't yet steer mid-segment rises.
            if (diagonal || !config.allowVertical) continue

            // ---- Step-up (+1 y) ----
            if (canJumpFromHere) {
                val up = BlockPos(origin.x + dx, origin.y + 1, origin.z + dz)
                if (isTraversable(up)) {
                    result[up.toFastVector()] = STEP_UP_COST
                }
            }

            // ---- Step-down (-1 y) ----
            // Walking off a ledge: target column must be standable, and the
            // block at the destination column at the player's chest-level
            // (origin.y) must be passable so the player doesn't clip the lip
            // while stepping off.
            val down = BlockPos(origin.x + dx, origin.y - 1, origin.z + dz)
            val frontHead = BlockPos(origin.x + dx, origin.y, origin.z + dz)
            if (isTraversable(down) && isPassableColumnSlice(frontHead)) {
                result[down.toFastVector()] = STEP_DOWN_COST
            }
        }

        // ---- Gap jumps (cardinal, 2 blocks forward, same or +1 y) ----
        if (config.allowJump && canJumpFromHere) {
            var ji = 0
            while (ji < cardinalOffsets.size) {
                val jdx = cardinalOffsets[ji]
                val jdz = cardinalOffsets[ji + 1]
                ji += 2

                val gapBlock = BlockPos(origin.x + jdx, origin.y, origin.z + jdz)
                // The gap must be clear for the full jump arc. At the apex the
                // player's head reaches ~y+3, so both the gap block and the
                // block above it need clearance.
                val gapClearFeet = isPassableColumnSlice(gapBlock)
                val gapClearHead = isPassableColumnSlice(BlockPos(gapBlock.x, gapBlock.y + 1, gapBlock.z))

                if (gapClearFeet && gapClearHead) {
                    // Same-y gap jump: jump across 1-block gap, land 2 blocks away flat.
                    val landFlat = BlockPos(origin.x + jdx * 2, origin.y, origin.z + jdz * 2)
                    if (isTraversable(landFlat)) {
                        result[landFlat.toFastVector()] = JUMP_COST
                    }

                    // +1y gap jump: jump across gap and up one block.
                    val landUp = BlockPos(origin.x + jdx * 2, origin.y + 1, origin.z + jdz * 2)
                    val jumpApex = BlockPos(origin.x + jdx, origin.y + 1, origin.z + jdz)
                    if (isTraversable(landUp) && isPassableColumnSlice(jumpApex) && isPassableColumnSlice(BlockPos(jumpApex.x, jumpApex.y + 1, jumpApex.z))) {
                        result[landUp.toFastVector()] = JUMP_UP_COST
                    }
                }
            }
        }

        return result
    }

    /**
     * Conservative node invalidation around a changed block.
     *
     * A changed block can affect the feet clearance, head clearance (now also
     * the jump-apex y+2 block), or support block for nearby nodes. Vertical
     * range is ±2 to cover step-up head clearance; horizontal range covers
     * diagonal corner-cuts and adjacent step-up/down cells.
     */
    fun affectedNodes(changedBlock: BlockPos): Set<FastVector> = buildSet {
        for (dx in -2..2) {
            for (dy in -2..2) {
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
     */
    private fun SafeContext.hasHeadClearance(origin: BlockPos): Boolean {
        val above = BlockPos(origin.x, origin.y + 1, origin.z)
        return isPassableColumnSlice(above)
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
    private const val COLLISION_EPSILON = 1.0E-6
    private const val SUPPORT_EPSILON = 1.0E-6
    private const val SUPPORT_INSET = 0.05
}
