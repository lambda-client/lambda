/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.construction.result

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.task.tasks.BuildTask.Companion.breakBlock
import net.minecraft.block.BlockState
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.awt.Color

/**
 * [PlaceResult] represents the result of a placement simulation.
 * Holds data about the placement and the result of the simulation.
 * Every [BuildResult] can [resolve] its own problem.
 * Every [BuildResult] can be compared to another [BuildResult].
 * First based on the context, then based on the [Rank].
 */
sealed class PlaceResult : BuildResult() {
    /**
     * Represents a successful placement. All checks have been passed.
     * @param context The context of the placement.
     */
    data class Place(
        override val blockPos: BlockPos,
        override val context: PlaceContext,
    ) : Contextual, Drawable, PlaceResult() {
        override val rank = Rank.PLACE_SUCCESS

        override fun SafeContext.buildRenderer() {
            with(context) { buildRenderer() }
        }

        override fun compareTo(other: ComparableResult<Rank>) =
            when (other) {
                is Place -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
    }

    /**
     * Represents a placement result where the block placement does not meet integrity expectations.
     *
     * This class is used to provide details about a block placement issue in which the actual block
     * placed does not match the expected state, or additional integrity conditions are not met.
     *
     * @property blockPos The position of the block being inspected or placed.
     * @property expected The expected state of the block.
     * @property simulated The context of the item placement simulation.
     * @property actual The expected
     */
    data class NoIntegrity(
        override val blockPos: BlockPos,
        val expected: BlockState,
        val simulated: ItemPlacementContext,
        val actual: BlockState? = null,
    ) : Drawable, PlaceResult() {
        override val rank = Rank.PLACE_NO_INTEGRITY
        private val color = Color(252, 3, 3, 100)

        override fun SafeContext.buildRenderer() {
            withState(expected, blockPos, color)
        }
    }

    /**
     * Represents a scenario where block placement is obstructed by an entity.
     *
     * @property blockPos The position of the block that was attempted to be placed.
     */
    data class BlockedByEntity(
        override val blockPos: BlockPos,
    ) : Navigable, PlaceResult() {
        override val rank = Rank.PLACE_BLOCKED_BY_PLAYER

        // ToDo: check what type of entity. player -> leave box, other entity -> kill?
        override val goal = GoalInverted(GoalBlock(blockPos))
    }

    /**
     * Represents a result indicating that a block cannot be replaced during a placement operation.
     *
     * @property blockPos The position of the block that cannot be replaced.
     * @property simulated The context of the item placement simulation.
     */
    data class CantReplace(
        override val blockPos: BlockPos,
        val simulated: ItemPlacementContext,
    ) : Resolvable, PlaceResult() {
        override val rank = Rank.PLACE_CANT_REPLACE

        override fun resolve() = breakBlock(blockPos)
    }

    /**
     * Represents a placement result indicating that the scaffolding placement has exceeded the allowed limits.
     *
     * @property blockPos The position of the block where the placement attempt occurred.
     * @property simulated The context of the simulated item placement attempt.
     */
    data class ScaffoldExceeded(
        override val blockPos: BlockPos,
        val simulated: ItemPlacementContext,
    ) : PlaceResult() {
        override val rank = Rank.PLACE_SCAFFOLD_EXCEEDED
    }

    /**
     * Represents a result where a block placement operation was prevented because
     * the relevant block feature is disabled.
     *
     * @property blockPos The position of the block that could not be placed.
     * @property itemStack The item stack associated with the attempted placement.
     */
    data class BlockFeatureDisabled(
        override val blockPos: BlockPos,
        val itemStack: ItemStack,
    ) : PlaceResult() {
        override val rank = Rank.PLACE_BLOCK_FEATURE_DISABLED
    }

    /**
     * Represents a result state where the placement or manipulation of a block resulted in an unexpected position.
     *
     * @property blockPos The intended position of the block.
     * @property actualPos The actual position of the block, which differs from the intended position.
     */
    data class UnexpectedPosition(
        override val blockPos: BlockPos,
        val actualPos: BlockPos,
    ) : PlaceResult() {
        override val rank = Rank.UNEXPECTED_POSITION
    }

    /**
     * Represents a result indicating an illegal usage during a placement operation.
     * E.g., the player can't modify the world or the block cannot be placed against the surface.
     *
     * @property blockPos The position of the block associated with the illegal usage result.
     * @property rank The ranking of this result, which is always `PLACE_ILLEGAL_USAGE`.
     */
    data class IllegalUsage(
        override val blockPos: BlockPos,
    ) : PlaceResult() {
        override val rank = Rank.PLACE_ILLEGAL_USAGE
    }

    /**
     * Represents the result of a place operation where the provided item does not match the expected item block type.
     *
     * @property blockPos The position of the block where the operation was attempted.
     * @property itemStack The item stack that was checked during the place operation.
     */
    data class NotItemBlock(
        override val blockPos: BlockPos,
        val itemStack: ItemStack,
    ) : PlaceResult() {
        override val rank = Rank.PLACE_NOT_ITEM_BLOCK
    }
}
