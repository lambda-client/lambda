package com.lambda.interaction.construction.result

import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.MainHandContainer
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.GoalTask.Companion.moveToBlock
import com.lambda.task.tasks.GoalTask.Companion.moveUntilLoaded
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

abstract class BuildResult : ComparableResult<Rank> {
    abstract val blockPos: BlockPos

    /**
     * The build action is done.
     */
    data class Done(
        override val blockPos: BlockPos,
    ) : BuildResult() {
        override val rank = Rank.DONE
    }

    /**
     * The build action is ignored.
     */
    data class Ignored(
        override val blockPos: BlockPos,
    ) : BuildResult() {
        override val rank = Rank.IGNORED
    }

    /**
     * The chunk at the target is not loaded.
     * @param blockPos The position of the block that is in an unloaded chunk.
     */
    data class ChunkNotLoaded(
        override val blockPos: BlockPos
    ) : Resolvable, BuildResult() {
        override val rank = Rank.CHUNK_NOT_LOADED

        override val resolve = moveUntilLoaded(blockPos)

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is ChunkNotLoaded -> blockPos.compareTo(other.blockPos)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * The player has no permission to interact with the block. (E.g.: Spectator mode)
     * @param blockPos The position of the block that is restricted.
     */
    data class Restricted(
        override val blockPos: BlockPos
    ) : BuildResult() {
        override val rank = Rank.BREAK_RESTRICTED
    }

    /**
     * The block needs server permission to be broken. (Needs op)
     * @param blockPos The position of the block that needs permission.
     * @param blockState The state of the block that needs permission.
     */
    data class NoPermission(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : BuildResult() {
        override val rank = Rank.BREAK_NO_PERMISSION
    }

    /**
     * The break target is out of the world border or height limit.
     * @param blockPos The position of the block that is out of the world.
     */
    data class OutOfWorld(
        override val blockPos: BlockPos
    ) : BuildResult() {
        override val rank = Rank.BREAK_OUT_OF_WORLD
    }

    /**
     * The block is unbreakable.
     * @param blockPos The position of the block that is unbreakable.
     * @param blockState The state of the block that is unbreakable.
     */
    data class Unbreakable(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : BuildResult() {
        override val rank = Rank.UNBREAKABLE
    }

    /**
     * The checked configuration hits on a side not in the player direction.
     * @param blockPos The position of the block that is not exposed.
     * @param side The side that is not exposed.
     */
    data class NotVisible(
        override val blockPos: BlockPos,
        val side: Direction,
        val distance: Double
    ) : Resolvable, BuildResult() {
        override val rank = Rank.NOT_VISIBLE

        override val resolve = emptyTask()

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is NotVisible -> distance.compareTo(other.distance)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * Player has an inefficient tool equipped.
     * @param neededItem The best tool for the block state.
     */
    data class WrongItem(
        override val blockPos: BlockPos,
        val context: BuildContext,
        val neededItem: Item
    ) : Resolvable, BuildResult() {
        override val rank = Rank.WRONG_ITEM

        override val resolve: Task<*> =
            neededItem.select().transfer(MainHandContainer)?.solve ?: emptyTask() // ToDo: Should throw error

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is WrongItem -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * Represents a break out of reach.
     * @param blockPos The position of the block that is out of reach.
     * @param distance The distance to the hit vector.
     */
    data class OutOfReach(
        override val blockPos: BlockPos,
        val distance: Double
    ) : Resolvable, BuildResult() {
        override val rank = Rank.OUT_OF_REACH

        override val resolve = moveToBlock(blockPos)

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is OutOfReach -> distance.compareTo(other.distance)
                else -> super.compareTo(other)
            }
        }
    }
}