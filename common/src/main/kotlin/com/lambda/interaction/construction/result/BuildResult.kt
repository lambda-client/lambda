package com.lambda.interaction.construction.result

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import baritone.process.BuilderProcess.GoalPlace
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.MainHandContainer
import com.lambda.task.Task
import com.lambda.task.Task.Companion.failTask
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

abstract class BuildResult : ComparableResult<Rank>, Task<Unit>() {
    abstract val blockPos: BlockPos
    open val pausesParent = false

    /**
     * The build action is done.
     */
    data class Done(
        override val blockPos: BlockPos
    ) : BuildResult() {
        override val rank = Rank.DONE
    }

    /**
     * The build action is ignored.
     */
    data class Ignored(
        override val blockPos: BlockPos
    ) : BuildResult() {
        override val rank = Rank.IGNORED
    }

    /**
     * The chunk at the target is not loaded.
     * @param blockPos The position of the block that is in an unloaded chunk.
     */
    data class ChunkNotLoaded(
        override val blockPos: BlockPos
    ) : Navigable, Drawable, BuildResult() {
        override val rank = Rank.CHUNK_NOT_LOADED
        private val color = Color(252, 165, 3, 100)

        override val goal = GoalBlock(blockPos)

        override fun SafeContext.buildRenderer() {
            withBox(Box(blockPos), color)
        }

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
    ) : Drawable, BuildResult() {
        override val rank = Rank.BREAK_RESTRICTED
        private val color = Color(255, 0, 0, 100)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The block needs server permission to be broken. (Needs op)
     * @param blockPos The position of the block that needs permission.
     * @param blockState The state of the block that needs permission.
     */
    data class NoPermission(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : Drawable, BuildResult() {
        override val rank get() = Rank.BREAK_NO_PERMISSION
        private val color = Color(255, 0, 0, 100)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The break target is out of the world border or height limit.
     * @param blockPos The position of the block that is out of the world.
     */
    data class OutOfWorld(
        override val blockPos: BlockPos
    ) : Drawable, BuildResult() {
        override val rank = Rank.OUT_OF_WORLD
        private val color = Color(3, 148, 252, 100)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The block is unbreakable.
     * @param blockPos The position of the block that is unbreakable.
     * @param blockState The state of the block that is unbreakable.
     */
    data class Unbreakable(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : Drawable, BuildResult() {
        override val rank = Rank.UNBREAKABLE
        private val color = Color(11, 11, 11, 100)
        
        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The checked configuration hits on a side not in the player direction.
     * @param blockPos The position of the block that is not exposed.
     * @param side The side that is not exposed.
     */
    data class NotVisible(
        override val blockPos: BlockPos,
        val hitPos: BlockPos,
        val side: Direction,
        val distance: Double
    ) : Drawable, BuildResult() {
        override val rank = Rank.NOT_VISIBLE
        private val color = Color(46, 0, 0, 80)

        override fun SafeContext.buildRenderer() {
            withBox(Box(blockPos), color)
        }

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
    ) : Drawable, BuildResult() {
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 100)

        override val pausesParent get() = true

        override fun SafeContext.onStart() {
            neededItem.select()
                .transfer(MainHandContainer)
                ?.onSuccess { _, _ ->
                    success(Unit)
                }?.start(this@WrongItem) ?: failure("Item ${neededItem.name.string} not found")
        }

        override fun SafeContext.buildRenderer() {
            if (blockPos.blockState(world).isAir) {
                withBox(Box(blockPos), color)
            } else {
                withPos(blockPos, color)
            }
        }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is WrongItem -> context.compareTo(other.context)
                else -> super.compareTo(other)
            }
        }
    }

    /**
     * The Player has the wrong item stack selected.
     * @param blockPos The position of the block that needs a different tool.
     * @param neededStack The best tool for the block state.
     */
    data class WrongStack(
        override val blockPos: BlockPos,
        val context: BuildContext,
        val neededStack: ItemStack
    ) : Drawable, BuildResult() {
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 100)

        override val pausesParent get() = true

        override fun SafeContext.onStart() {
            neededStack.select()
                .transfer(MainHandContainer)
                ?.onSuccess { _, _ ->
                    success(Unit)
                }?.start(this@WrongStack) ?: failTask("Stack ${neededStack.name.string} not found")
        }

        override fun SafeContext.buildRenderer() {
            if (blockPos.blockState(world).isAir) {
                withBox(Box(blockPos), color)
            } else {
                withPos(blockPos, color)
            }
        }

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
     * @param pov The point of view of the player.
     * @param misses The points that are out of reach.
     */
    data class OutOfReach(
        override val blockPos: BlockPos,
        val pov: Vec3d,
        val misses: Set<Vec3d>
    ) : Navigable, Drawable, BuildResult() {
        override val rank = Rank.OUT_OF_REACH
        private val color = Color(252, 3, 207, 100)

        val distance: Double by lazy {
            misses.minOfOrNull { pov.distanceTo(it) } ?: 0.0
        }

        override val goal = GoalNear(blockPos, 3)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is OutOfReach -> distance.compareTo(other.distance)
                else -> super.compareTo(other)
            }
        }
    }
}