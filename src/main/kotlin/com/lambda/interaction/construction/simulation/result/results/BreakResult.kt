/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.construction.simulation.result.results

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import com.lambda.context.Automated
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.util.DirectionMask.mask
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.ComparableResult
import com.lambda.interaction.construction.simulation.result.Contextual
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.construction.simulation.result.Navigable
import com.lambda.interaction.construction.simulation.result.Rank
import com.lambda.interaction.construction.simulation.result.Resolvable
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.selectStack
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.task.tasks.transferTo
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import java.awt.Color

sealed class BreakResult : BuildResult() {
    override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"

    /**
     * The break target is out of the world border.
     * @param pos The position of the block that is outside the world border.
     */
    data class OutOfBorder(
        override val pos: BlockPos,
    ) : Drawable, BreakResult() {
        override val name: String get() = "$pos is outside the world border."
        override val rank = Rank.OutOfWorld
        private val color = Color(3, 148, 252, 100)

        override fun RenderBuilder.render() {
            box(pos) {
                allColors(color)
            }
        }
    }

    /**
     * Represents a successful break. All checks have been passed.
     * @param context The context of the break.
     */
    data class Break(
        override val pos: BlockPos,
        override val context: BreakContext,
    ) : Contextual, Drawable, BreakResult() {
        override val rank = Rank.BreakSuccess

        override fun RenderBuilder.render() {
            with(context) { render() }
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
        override val rank = Rank.BreakNotExposed
        private val color = Color(46, 0, 0, 30)

        override fun RenderBuilder.render() {
            box(pos) {
                allColors(color)
                hideSides(side.mask.inv())
            }
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
    ) : Resolvable, BreakResult() {
        override val rank = Rank.BreakItemCantMine

        context(_: Automated)
        override fun resolve() =
            selectStack { inverted { isItem(badItem) } }
                .transferTo(HotbarContainer)

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is ItemCantMine -> badItem.name.string.compareTo(other.badItem.name.string)
                else -> super.compareResult(other)
            }
    }

    /**
     * The block is a fluid and first has to be submerged.
     * @param pos The position of the block that is a fluid.
     */
    data class Submerge(
        override val pos: BlockPos,
        val blockState: BlockState
    ) : Drawable, BreakResult() {
        override val rank = Rank.BreakSubmerge
        private val color = Color(114, 27, 255, 100)

        override fun RenderBuilder.render() {
            box(pos) {
                allColors(color)
            }
        }
    }

    /**
     * The block is blocked by another fluid block that first has to be submerged.
     */
    data class BlockedByFluid(
        override val pos: BlockPos,
        val blockState: BlockState,
        val affectedFluids: Set<BlockPos>
    ) : Drawable, BreakResult() {
        override val rank = Rank.BreakIsBlockedByFluid
        private val color = Color(50, 12, 112, 100)

        override fun RenderBuilder.render() {
            val center = pos.toCenterPos()
            val box = Box(
                center.x - 0.1, center.y - 0.1, center.z - 0.1,
                center.x + 0.1, center.y + 0.1, center.z + 0.1
            )
            box(box) {
                allColors(color)
            }
        }
    }

    /**
     * The player is standing on the block.
     */
    data class PlayerOnTop(
        override val pos: BlockPos,
        val blockState: BlockState,
    ) : Navigable, Drawable, BreakResult() {
        override val rank = Rank.BreakPlayerOnTop
        private val color = Color(252, 3, 207, 100)

        override val goal = if (BaritoneHandler.baritoneAvailable) GoalInverted(GoalBlock(pos)) else null

        override fun RenderBuilder.render() {
            box(pos) {
                allColors(color)
            }
        }
    }

    data class Dependency(
        override val pos: BlockPos,
        override val dependency: BuildResult
    ) : BreakResult(), Dependent by Dependent.Nested(dependency) {
        override val rank = dependency.rank
        override val compareBy = lastDependency
    }
}
