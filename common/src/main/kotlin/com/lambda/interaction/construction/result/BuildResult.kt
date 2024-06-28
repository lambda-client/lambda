package com.lambda.interaction.construction.result

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import baritone.process.BuilderProcess.GoalPlace
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.ContainerManager.transfer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.MainHandContainer
import com.lambda.task.Task.Companion.failTask
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

abstract class BuildResult : ComparableResult<Rank> {
    abstract val blockPos: BlockPos
    open val pausesParent = false

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
    ) : Navigable, Drawable, BuildResult() {
        override val rank = Rank.NOT_VISIBLE
        private val color = Color(46, 0, 0, 30)

        override val goal = GoalPlace(blockPos)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color, side)
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
    ) : Resolvable, Drawable, BuildResult() {
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 100)

        override val pausesParent get() = true

        override val resolve get() =
            neededItem.select().transfer(MainHandContainer)?.solve ?: failTask("Item ${neededItem.name.string} not found")

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
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
    ) : Resolvable, Drawable, BuildResult() {
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 100)

        override val pausesParent get() = true

        override val resolve get() =
            neededStack.select().transfer(MainHandContainer)?.solve ?: failTask("Stack ${neededStack.name.string} not found")

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
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
     * @param startVec The start vector of the reach.
     * @param hitVec The hit vector of the reach.
     * @param reach The maximum reach distance.
     * @param side The side that is out of reach.
     */
    data class OutOfReach(
        override val blockPos: BlockPos,
        val startVec: Vec3d,
        val hitVec: Vec3d,
        val reach: Double,
        val side: Direction,
    ) : Navigable, Drawable, BuildResult() {
        override val rank = Rank.OUT_OF_REACH
        private val color = Color(252, 3, 207, 100)

        val distance: Double by lazy {
            startVec.distanceTo(hitVec)
        }

        override val goal = GoalNear(blockPos, 2)

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