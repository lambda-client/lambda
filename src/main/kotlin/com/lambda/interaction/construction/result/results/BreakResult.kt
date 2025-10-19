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

package com.lambda.interaction.construction.result.results

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import com.lambda.context.Automated
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.ComparableResult
import com.lambda.interaction.construction.result.Contextual
import com.lambda.interaction.construction.result.Dependent
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.Navigable
import com.lambda.interaction.construction.result.Rank
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.container.containers.MainHandContainer
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
        override val pos: BlockPos,
        override val context: BreakContext,
    ) : Drawable, Contextual, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakSuccess

        override fun ShapeBuilder.buildRenderer() {
            with(context) { buildRenderer() }
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is Break -> context.compareTo(other.context)
                else -> super.compareResult(other)
            }
    }

    /**
     * Represents a break configuration where the hit side is not exposed to air.
     * @param pos The position of the block that is not exposed.
     * @param side The side that is not exposed.
     */
    data class NotExposed(
        override val pos: BlockPos,
        val side: Direction,
    ) : Drawable, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakNotExposed
        private val color = Color(46, 0, 0, 30)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color, side.mask)
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is NotExposed -> pos.compareTo(other.pos)
                else -> super.compareResult(other)
            }
    }

    /**
     * The equipped item is not suitable for breaking blocks.
     * @param blockState The block state that is being broken.
     * @param badItem The item that is being used.
     */
    data class ItemCantMine(
        override val pos: BlockPos,
        val blockState: BlockState,
        val badItem: Item
    ) : Drawable, Resolvable, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakItemCantMine
        private val color = Color(255, 0, 0, 100)

        context(automated: Automated)
        override fun resolve() =
            selectStack {
                isItem(badItem).not()
            }.let { selection ->
                selection.transfer(MainHandContainer)
                    ?: MaterialContainer.AwaitItemTask(
                        "Couldn't find a tool for ${blockState.block.name.string} with $badItem in main hand.",
                        selection,
                        automated
                    )
            }

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is ItemCantMine -> badItem.name.string.compareTo(other.badItem.name.string)
                else -> super.compareResult(other)
            }
    }

    /**
     * The block is a liquid and first has to be submerged.
     * @param pos The position of the block that is a liquid.
     */
    data class Submerge(
        override val pos: BlockPos,
        val blockState: BlockState
    ) : Drawable, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakSubmerge
        private val color = Color(114, 27, 255, 100)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }
    }

    /**
     * The block is blocked by another liquid block that first has to be submerged.
     */
    data class BlockedByFluid(
        override val pos: BlockPos,
        val blockState: BlockState,
    ) : Drawable, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakIsBlockedByFluid
        private val color = Color(50, 12, 112, 100)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }
    }

    /**
     * The player is standing on the block.
     */
    data class PlayerOnTop(
        override val pos: BlockPos,
        val blockState: BlockState,
    ) : Navigable, Drawable, BreakResult() {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = Rank.BreakPlayerOnTop
        private val color = Color(252, 3, 207, 100)

        override val goal = GoalInverted(GoalBlock(pos))

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }
    }

    data class Dependency(
        override val pos: BlockPos,
        override val dependency: BuildResult
    ) : BreakResult(), Dependent by Dependent.Nested(dependency) {
        override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"
        override val rank = dependency.rank
        override val compareBy = lastDependency
    }
}
