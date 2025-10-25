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

import baritone.api.pathing.goals.GoalNear
import com.lambda.context.Automated
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.ComparableResult
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.Navigable
import com.lambda.interaction.construction.result.Rank
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.container.containers.MainHandContainer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color

sealed class GenericResult : BuildResult() {
    /**
     * The checked configuration hits on a side not in the player direction.
     * @param pos The position of the block that is not exposed.
     * @param side The side that is not exposed.
     */
    data class NotVisible(
        override val pos: BlockPos,
        val hitPos: BlockPos,
        val distance: Double
    ) : Drawable, GenericResult() {
        override val name: String get() = "Not visible at $pos."
        override val rank = Rank.NotVisible
        private val color = Color(46, 0, 0, 80)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }

        override fun compareResult(other: ComparableResult<Rank>): Int {
            return when (other) {
                is NotVisible -> distance.compareTo(other.distance)
                else -> super.compareResult(other)
            }
        }
    }

    /**
     * The build action is ignored.
     */
    data class Ignored(
        override val pos: BlockPos,
    ) : GenericResult() {
        override val name: String
            get() = "Build at $pos is ignored."
        override val rank = Rank.Ignored
    }

    /**
     * Player has an inefficient tool equipped.
     * @param neededSelection The best tool for the block state.
     */
    data class WrongItemSelection(
        override val pos: BlockPos,
        val neededSelection: StackSelection,
        val currentItem: ItemStack
    ) : Drawable, Resolvable, GenericResult() {
        override val name: String get() = "Wrong item ($currentItem) for ${pos.toShortString()} need $neededSelection"
        override val rank = Rank.WrongItem
        private val color = Color(3, 252, 169, 25)

        context(automated: Automated)
        override fun resolve() =
            neededSelection.transfer(MainHandContainer)
                ?: MaterialContainer.AwaitItemTask(
                    "Couldn't find $neededSelection anywhere.",
                    neededSelection,
                    automated
                )

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }
    }

    /**
     * Represents a break out of reach.
     * @param pos The position of the block that is out of reach.
     * @param pov The point of view of the player.
     * @param misses The points that are out of reach.
     */
    data class OutOfReach(
        override val pos: BlockPos,
        val pov: Vec3d,
        val misses: Set<Vec3d>,
    ) : Navigable, Drawable, GenericResult() {
        override val name: String get() = "Out of reach at $pos."
        override val rank = Rank.OutOfReach
        private val color = Color(252, 3, 207, 25)

        val distance: Double by lazy {
            misses.minOfOrNull { pov.distanceTo(it) } ?: 0.0
        }

        override val goal = GoalNear(pos, 3)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }

        override fun compareResult(other: ComparableResult<Rank>): Int {
            return when (other) {
                is OutOfReach -> distance.compareTo(other.distance)
                else -> super.compareResult(other)
            }
        }
    }
}
