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

import baritone.api.pathing.goals.GoalBlock
import com.lambda.graphics.mc.renderer.TickedRenderer
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.ComparableResult
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.construction.simulation.result.Navigable
import com.lambda.interaction.construction.simulation.result.Rank
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import java.awt.Color

sealed class PreSimResult : BuildResult() {
    override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"

    /**
     * The build action is done.
     */
    data class Done(
        override val pos: BlockPos,
    ) : PreSimResult() {
        override val name: String
            get() = "Build at $pos is done."
        override val rank = Rank.Done
    }

    /**
     * The chunk at the target is not loaded.
     * @param pos The position of the block that is in an unloaded chunk.
     */
    data class ChunkNotLoaded(
        override val pos: BlockPos,
    ) : Navigable, Drawable, PreSimResult() {
        override val name: String get() = "Chunk at $pos is not loaded."
        override val rank = Rank.ChunkNotLoaded
        private val color = Color(252, 165, 3, 100)

        override val goal = GoalBlock(pos)

        override fun render(esp: TickedRenderer) {
            esp.shapes {
                box(pos, 1.5f) {
                    allColors(color)
                }
            }
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is ChunkNotLoaded -> pos.compareTo(other.pos)
                else -> super.compareResult(other)
            }
    }

    /**
     * The player has no permission to interact with the block. (E.g.: Adventure mode)
     * @param pos The position of the block that is restricted.
     */
    data class Restricted(
        override val pos: BlockPos,
    ) : Drawable, PreSimResult() {
        override val name: String get() = "Restricted at $pos."
        override val rank = Rank.BreakRestricted
        private val color = Color(255, 0, 0, 100)

        override fun render(esp: TickedRenderer) {
            esp.shapes {
                box(pos, 1.5f) {
                    allColors(color)
                }
            }
        }
    }

    /**
     * The block needs server permission to be broken. (Needs op)
     * @param pos The position of the block that needs permission.
     * @param blockState The state of the block that needs permission.
     */
    data class NoPermission(
        override val pos: BlockPos,
        val blockState: BlockState,
    ) : Drawable, PreSimResult() {
        override val name: String get() = "No permission at $pos."
        override val rank get() = Rank.BreakNoPermission
        private val color = Color(255, 0, 0, 100)

        override fun render(esp: TickedRenderer) {
            esp.shapes {
                box(pos, 1.5f) {
                    allColors(color)
                }
            }
        }
    }

    /**
     * The break target is out of the world border or height limit.
     * @param pos The position of the block that is out of the world.
     */
    data class OutOfWorld(
        override val pos: BlockPos,
    ) : Drawable, PreSimResult() {
        override val name: String get() = "$pos is out of the world."
        override val rank = Rank.OutOfWorld
        private val color = Color(3, 148, 252, 100)

        override fun render(esp: TickedRenderer) {
            esp.shapes {
                box(pos, 1.5f) {
                    allColors(color)
                }
            }
        }
    }

    /**
     * The block is unbreakable.
     * @param pos The position of the block that is unbreakable.
     * @param blockState The state of the block that is unbreakable.
     */
    data class Unbreakable(
        override val pos: BlockPos,
        val blockState: BlockState,
    ) : Drawable, PreSimResult() {
        override val name: String get() = "Unbreakable at $pos."
        override val rank = Rank.Unbreakable
        private val color = Color(11, 11, 11, 100)

        override fun render(esp: TickedRenderer) {
            esp.shapes {
                box(pos, 1.5f) {
                    allColors(color)
                }
            }
        }
    }
}