
package com.minato.interaction.construction.simulation.result.results

import com.minato.context.AutomatedSafeContext
import com.minato.graphics.mc.RenderBuilder
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.ComparableResult
import com.minato.interaction.construction.simulation.result.Drawable
import com.minato.interaction.construction.simulation.result.Rank
import com.minato.interaction.construction.simulation.result.Resolvable
import com.minato.interaction.handlers.ContainerHandler.transferByTask
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.containers.HotbarContainer
import com.minato.task.Task
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
    ) : Drawable, GenericResult() {
        override val name: String get() = "Out of reach at $pos."
        override val rank = Rank.OutOfReach
        private val color = Color(252, 3, 207, 25)

        val distance: Double by lazy {
            misses.minOfOrNull { pov.distanceTo(it.first) } ?: 0.0
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

        override fun compareResult(other: ComparableResult<Rank>): Int {
            return when (other) {
                is OutOfReach -> distance.compareTo(other.distance)
                else -> super.compareResult(other)
            }
        }
    }
}
