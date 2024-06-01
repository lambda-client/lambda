package com.lambda.interaction.construction.result

import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.task.Task
import com.lambda.task.tasks.BuildStructure.Companion.breakBlock
import com.lambda.task.tasks.PlaceBlock.Companion.placeBlock
import net.minecraft.block.BlockState
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

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
    data class Success(
        override val blockPos: BlockPos,
        val context: PlaceContext,
    ) : Resolvable, PlaceResult() {
        override val rank = Rank.PLACE_SUCCESS

        override val resolve = placeBlock(context)

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is Success -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * The calculated placement configuration does not match the simulated outcome.
     * @param blockPos The position of the block that is not placed correctly.
     * @param expected The expected placement configuration.
     * @param simulated The simulated placement configuration.
     */
    data class NoIntegrity(
        override val blockPos: BlockPos,
        val expected: BlockState,
        val simulated: ItemPlacementContext
    ) : PlaceResult() {
        override val rank = Rank.PLACE_NO_INTEGRITY
    }

    /**
     * The placement configuration cannot replace the block at the target position.
     * @param simulated The simulated placement configuration.
     */
    data class CantReplace(
        override val blockPos: BlockPos,
        val simulated: ItemPlacementContext
    ) : Resolvable, PlaceResult() {
        override val rank = Rank.PLACE_CANT_REPLACE

        override val resolve = breakBlock(simulated.blockPos)
    }

    /**
     * The placement configuration exceeds the maximum scaffold distance.
     * @param simulated The simulated placement configuration.
     */
    data class ScaffoldExceeded(
        override val blockPos: BlockPos,
        val simulated: ItemPlacementContext
    ) : PlaceResult() {
        override val rank = Rank.PLACE_SCAFFOLD_EXCEEDED
    }

    /**
     * The interaction with the block is restricted due to the feature being disabled.
     */
    data class BlockFeatureDisabled(
        override val blockPos: BlockPos,
        val itemStack: ItemStack,
    ) : PlaceResult() {
        override val rank = Rank.PLACE_BLOCK_FEATURE_DISABLED
    }

    /**
     * The player has no permission to interact with the block. Or the stack cannot be used on the block.
     */
    data class IllegalUsage(
        override val blockPos: BlockPos
    ) : PlaceResult() {
        override val rank = Rank.PLACE_ILLEGAL_USAGE
    }

    /**
     * The item stack is not a block item.
     * This will be resolved by analyzing interaction with non-block items.
     */
    data class NotItemBlock(
        override val blockPos: BlockPos,
        val itemStack: ItemStack
    ) : Resolvable, PlaceResult() {
        override val rank = Rank.PLACE_NOT_ITEM_BLOCK

        override val resolve = Task.emptyTask() // ToDo: analyze interaction with non-block items
    }
}