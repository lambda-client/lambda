/*
 * Copyright 2024 Lambda
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
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.material.container.ContainerManager.findBestAvailableTool
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.task.tasks.BreakBlock
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.awt.Color

sealed class BreakResult : BuildResult() {

    /**
     * Represents a successful break. All checks have been passed.
     * @param context The context of the break.
     */
    data class Break(
        override val blockPos: BlockPos,
        val context: BreakContext
    ) : Drawable, Resolvable, BreakResult() {
        override val rank = Rank.BREAK_SUCCESS
        private val color = Color(222, 0, 0, 100)

        var collectDrop = false
        override val pausesParent get() = collectDrop

        override fun resolve() = BreakBlock(context, collectDrop)

        override fun SafeContext.buildRenderer() {
            withPos(context.expectedPos, color, context.result.side)
        }

        override fun compareTo(other: ComparableResult<Rank>): Int {
            return when (other) {
                is Break -> context.compareTo(other.context)
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
    ) : Drawable, BreakResult() {
        override val rank = Rank.BREAK_NOT_EXPOSED
        private val color = Color(46, 0, 0, 30)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color, side)
        }

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
    ) : Drawable, Resolvable, BreakResult() {
        override val rank = Rank.BREAK_ITEM_CANT_MINE
        private val color = Color(255, 0, 0, 100)

        override val pausesParent get() = true

        override fun resolve() =
            findBestAvailableTool(blockState)
                ?.select()
                ?.transfer(MainHandContainer)
                ?: selectStack {
                    isItem(badItem).not()
                }.transfer(MainHandContainer)
                ?: MaterialContainer.Nothing()

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
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
    ) : Drawable, BreakResult() {
        override val rank = Rank.BREAK_SUBMERGE
        private val color = Color(114, 27, 255, 100)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The block is blocked by another liquid block that first has to be submerged.
     */
    data class BlockedByLiquid(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : Drawable, BreakResult() {
        override val rank = Rank.BREAK_IS_BLOCKED_BY_LIQUID
        private val color = Color(50, 12, 112, 100)

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }

    /**
     * The player is standing on the block.
     */
    data class PlayerOnTop(
        override val blockPos: BlockPos,
        val blockState: BlockState
    ) : Navigable, Drawable, BreakResult() {
        override val rank = Rank.BREAK_PLAYER_ON_TOP
        private val color = Color(252, 3, 207, 100)

        override val goal = GoalInverted(GoalBlock(blockPos))

        override fun SafeContext.buildRenderer() {
            withPos(blockPos, color)
        }
    }
}
