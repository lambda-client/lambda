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
import baritone.api.pathing.goals.GoalNear
import com.lambda.config.groups.InventoryConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Nameable
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

abstract class BuildResult : ComparableResult<Rank>, Nameable {
    abstract val blockPos: BlockPos
    open val pausesParent = false
    override val name: String get() = "${this::class.simpleName} at ${blockPos.toShortString()}"

    interface Contextual {
        val context: BuildContext
    }

    /**
     * The build action is done.
     */
    data class Done(
        override val blockPos: BlockPos,
    ) : BuildResult() {
        override val name: String
            get() = "Build at $blockPos is done."
        override val rank = Rank.DONE
    }

    /**
     * The build action is ignored.
     */
    data class Ignored(
        override val blockPos: BlockPos,
    ) : BuildResult() {
        override val name: String
            get() = "Build at $blockPos is ignored."
        override val rank = Rank.IGNORED
    }

    /**
     * The chunk at the target is not loaded.
     * @param blockPos The position of the block that is in an unloaded chunk.
     */
    data class ChunkNotLoaded(
        override val blockPos: BlockPos,
    ) : Navigable, Drawable, BuildResult() {
        override val name: String get() = "Chunk at $blockPos is not loaded."
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
        override val blockPos: BlockPos,
    ) : Drawable, BuildResult() {
        override val name: String get() = "Restricted at $blockPos."
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
        val blockState: BlockState,
    ) : Drawable, BuildResult() {
        override val name: String get() = "No permission at $blockPos."
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
        override val blockPos: BlockPos,
    ) : Drawable, BuildResult() {
        override val name: String get() = "$blockPos is out of the world."
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
        val blockState: BlockState,
    ) : Drawable, BuildResult() {
        override val name: String get() = "Unbreakable at $blockPos."
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
        val distance: Double,
    ) : Drawable, BuildResult() {
        override val name: String get() = "Not visible at $blockPos."
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
     * @param neededSelection The best tool for the block state.
     */
    data class WrongItemSelection(
        override val blockPos: BlockPos,
        val context: BuildContext,
        val neededSelection: StackSelection,
        val currentItem: ItemStack,
        val inventory: InventoryConfig
    ) : Drawable, Resolvable, BuildResult() {
        override val name: String get() = "Wrong item ($currentItem) for ${blockPos.toShortString()} need $neededSelection"
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 25)

        override val pausesParent get() = true

        override fun resolve() = neededSelection
            .transfer(MainHandContainer, inventory) ?: MaterialContainer.FailureTask("Couldn't find $neededSelection anywhere.")

        override fun SafeContext.buildRenderer() {
            if (blockState(blockPos).isAir) {
                withBox(Box(blockPos), color)
            } else {
                withPos(blockPos, color)
            }
        }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is WrongItemSelection -> context.compareTo(other.context)
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
        val neededStack: ItemStack,
        val inventory: InventoryConfig
    ) : Drawable, Resolvable, BuildResult() {
        override val name: String get() = "Wrong stack for ${blockPos.toShortString()} need $neededStack."
        override val rank = Rank.WRONG_ITEM
        private val color = Color(3, 252, 169, 25)

        override val pausesParent get() = true

        override fun resolve() =
            neededStack.select()
                .transfer(MainHandContainer, inventory) ?: MaterialContainer.FailureTask("Couldn't find ${neededStack.item.name.string} anywhere.")

        override fun SafeContext.buildRenderer() {
            if (blockState(blockPos).isAir) {
                withBox(Box(blockPos), color)
            } else {
                withPos(blockPos, color)
            }
        }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is WrongItemSelection -> context.compareTo(other.context)
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
        val misses: Set<Vec3d>,
    ) : Navigable, Drawable, BuildResult() {
        override val name: String get() = "Out of reach at $blockPos."
        override val rank = Rank.OUT_OF_REACH
        private val color = Color(252, 3, 207, 25)

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
