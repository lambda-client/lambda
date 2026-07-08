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

package com.lambda.worldview

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.EmptyBlockView
import kotlin.math.ceil

/**
 * Precomputed physical traits of one blockstate — the planner's hot path
 * reads these ints and booleans instead of touching Minecraft collision
 * shapes (research plan §4.2, after leijurv's BlockStateCachedData).
 *
 * Traits are context-free: computed once per state id against an empty
 * world view. Blocks whose shape depends on world context (scaffolding,
 * moving pistons) get conservative values ([exact] = false) — the planner
 * treats them as impassable and unstandable rather than guessing.
 */
class BlockTraits(
    /** Collision shape is fully empty (air, grass, torches, rails, …). */
    val passable: Boolean,
    /**
     * The centered player footprint (0.6 × 0.6, full voxel height) does not
     * intersect this state's collision shape. Weaker than [passable]: open
     * doors, panes on a far edge, and end rods leave the center column free.
     */
    val centerPassable: Boolean,
    /** The UP face fully supports a player — `isSideSolidFullSquare(UP)`. */
    val standableFullTop: Boolean,
    /**
     * Highest collision extent in blips (1/16 block): 16 for full blocks,
     * 8 for bottom slabs, 24 for fences/walls, 0 when [passable]. Values
     * above [BLIPS_PER_BLOCK] intrude into the voxel above.
     */
    val collisionTopBlips: Int,
    /** False when the shape is world-dependent and values are conservative. */
    val exact: Boolean,
) {
    /** Collision pokes above the top of the voxel into the one above it. */
    val intrudesAbove: Boolean get() = collisionTopBlips > BLIPS_PER_BLOCK

    companion object {
        const val BLIPS_PER_BLOCK = 16

        val CONSERVATIVE = BlockTraits(
            passable = false,
            centerPassable = false,
            standableFullTop = false,
            collisionTopBlips = 2 * BLIPS_PER_BLOCK,
            exact = false,
        )
    }
}

/**
 * Lazy per-state-id trait table over [Block.STATE_IDS]. Client thread only.
 */
object BlockTraitRegistry {
    private val cache: Array<BlockTraits?> by lazy { arrayOfNulls(Block.STATE_IDS.size()) }

    /**
     * Player footprint column used for [BlockTraits.centerPassable]:
     * horizontal extent of the 0.6-wide player box centered in the voxel,
     * full voxel height, contracted by the collision epsilon the box-based
     * checks used.
     */
    private val FOOTPRINT_COLUMN = VoxelShapes.cuboid(
        0.2 + EPSILON, EPSILON, 0.2 + EPSILON,
        0.8 - EPSILON, 1.0 - EPSILON, 0.8 - EPSILON,
    )

    fun of(stateId: Int): BlockTraits {
        val cached = cache.getOrNull(stateId) ?: return computeAndStore(stateId)
        return cached
    }

    fun of(state: BlockState): BlockTraits = of(idOf(state))

    fun idOf(state: BlockState): Int = Block.STATE_IDS.getRawId(state)

    val AIR_ID: Int by lazy { idOf(net.minecraft.block.Blocks.AIR.defaultState) }

    private fun computeAndStore(stateId: Int): BlockTraits {
        val state = Block.STATE_IDS.get(stateId) ?: return BlockTraits.CONSERVATIVE
        val traits = compute(state)
        if (stateId < cache.size) cache[stateId] = traits
        return traits
    }

    private fun compute(state: BlockState): BlockTraits {
        if (state.isAir) {
            return BlockTraits(
                passable = true,
                centerPassable = true,
                standableFullTop = false,
                collisionTopBlips = 0,
                exact = true,
            )
        }
        if (state.block.hasDynamicBounds()) return BlockTraits.CONSERVATIVE

        return try {
            val shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
            val passable = shape.isEmpty
            BlockTraits(
                passable = passable,
                centerPassable = passable || !VoxelShapes.matchesAnywhere(shape, FOOTPRINT_COLUMN, BooleanBiFunction.AND),
                standableFullTop = state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.UP),
                collisionTopBlips = if (passable) 0 else ceil(shape.getMax(Direction.Axis.Y) * BlockTraits.BLIPS_PER_BLOCK).toInt(),
                exact = true,
            )
        } catch (_: Exception) {
            // A state that insists on world context despite static bounds:
            // never guess on the planning path.
            BlockTraits.CONSERVATIVE
        }
    }
}

private const val EPSILON = 1.0E-6
