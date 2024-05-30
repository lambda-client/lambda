package com.lambda.interaction.construction.result

import com.lambda.task.tasks.GoalTask.Companion.moveUntilLoaded
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

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
    ) : BreakResult() {
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
    ) : BreakResult() {
        override val rank = Rank.BREAK_NO_PERMISSION
    }

    /**
     * The break target is out of the world border or height limit.
     * @param blockPos The position of the block that is out of the world.
     */
    data class OutOfWorld(
        override val blockPos: BlockPos
    ) : BreakResult() {
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
    ) : BreakResult() {
        override val rank = Rank.BREAK_UNBREAKABLE
    }
}