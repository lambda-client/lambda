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

package com.lambda.interaction.construction.simulation.result.results

import baritone.api.pathing.goals.GoalNear
import com.lambda.context.AutomatedSafeContext
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.ComparableResult
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.construction.simulation.result.Navigable
import com.lambda.interaction.construction.simulation.result.Rank
import com.lambda.interaction.construction.simulation.result.Resolvable
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.ContainerManager.transferByTask
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.task.Task
import net.minecraft.client.data.TextureMap.side
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
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

        override fun RenderBuilder.render() {
            val box = with(pos) {
                Box(
                    x - 0.05, y - 0.05, z - 0.05,
                    x + 0.05, y + 0.05, z + 0.05,
                ).offset(pos)
            }
            box(box) {
                allColors(color)
            }
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

        context(task: Task<*>, _: AutomatedSafeContext)
        override fun resolve() {
            neededSelection.transferByTask(HotbarContainer)?.softFail()?.execute(task)
        }

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
     * Represents a break out of reach.
     * @param pos The position of the block that is out of reach.
     * @param pov The point of view of the player.
     * @param misses The points that are out of reach.
     */
    data class OutOfReach(
        override val pos: BlockPos,
        val pov: Vec3d,
        val misses: Set<Pair<Vec3d, Direction>>,
    ) : Navigable, Drawable, GenericResult() {
        override val name: String get() = "Out of reach at $pos."
        override val rank = Rank.OutOfReach
        private val color = Color(252, 3, 207, 25)

        val distance: Double by lazy {
            misses.minOfOrNull { pov.distanceTo(it.first) } ?: 0.0
        }

        override val goal = GoalNear(pos, 3)

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

        override fun compareResult(other: ComparableResult<Rank>): Int {
            return when (other) {
                is OutOfReach -> distance.compareTo(other.distance)
                else -> super.compareResult(other)
            }
        }
    }
}
