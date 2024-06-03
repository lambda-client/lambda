package com.lambda.interaction.construction.result

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.material.ContainerManager.findBestAvailableTool
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.MainHandContainer
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.BreakBlock.Companion.breakBlock
import com.lambda.task.tasks.GoalTask.Companion.moveToGoalUntil
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

sealed class BreakResult : BuildResult() {

    /**
     * Represents a successful break. All checks have been passed.
     * @param context The context of the break.
     */
    data class Success(
        override val blockPos: BlockPos,
        val context: BreakContext
    ) : Resolvable, BreakResult() {
        override val rank = Rank.BREAK_SUCCESS

        override val resolve get() = breakBlock(context)

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is Success -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * Represents a break configuration where the hit side is not exposed to air.
     * @param blockPos The position of the block that is not exposed.
     * @param side The side that is not exposed.
     */
    data class NotExposed(
        override val blockPos: BlockPos,
        val side: Direction
    ) : Resolvable, BreakResult() {
        override val rank = Rank.BREAK_NOT_EXPOSED

        override val resolve get() = emptyTask("Block is not exposed to air.")

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is NotExposed -> blockPos.compareTo(other.blockPos)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * The equipped item is not suitable for breaking blocks.
     * @param blockState The block state that is being broken.
     * @param badItem The item that is being used.
     */
    data class ItemCantMine(
        override val blockPos: BlockPos,
        val blockState: BlockState,
        val badItem: Item
    ) : Resolvable, BreakResult() {
        override val rank = Rank.BREAK_ITEM_CANT_MINE
        override val resolve get() = findBestAvailableTool(blockState)
                    ?.select()
                    ?.transfer(MainHandContainer)
                    ?.solve ?: run {
                        selectStack {
                            isItem(badItem).not()
                        }.transfer(MainHandContainer)?.solve ?: emptyTask("No item found or space") // ToDo: Should throw error
                    }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is ItemCantMine -> badItem.name.string.compareTo(other.badItem.name.string)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * The block is a liquid and first has to be submerged.
     * @param blockPos The position of the block that is a liquid.
     */
    data class Submerge(
        override val blockPos: BlockPos,
        val blockState: BlockState,
        val submerge: Set<BuildResult>
    ) : BreakResult() {
        override val rank = Rank.BREAK_SUBMERGE
    }

    /**
     * The block is blocked by another liquid block that first has to be submerged.
     */
    data class BlockedByLiquid(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : BreakResult() {
        override val rank = Rank.BREAK_IS_BLOCKED_BY_LIQUID
    }

    /**
     * The player is standing on the block.
     */
    data class PlayerOnTop(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : Resolvable, BreakResult() {
        override val rank = Rank.BREAK_PLAYER_ON_TOP

        override val resolve get() =
            moveToGoalUntil(GoalInverted(GoalBlock(blockPos))) {
                val pBox = player.boundingBox
                val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
                world.findSupportingBlockPos(player, aabb).orElse(null) != blockPos
            }
    }
}
