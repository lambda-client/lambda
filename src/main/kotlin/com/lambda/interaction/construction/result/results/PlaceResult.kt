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
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.ComparableResult
import com.lambda.interaction.construction.result.Contextual
import com.lambda.interaction.construction.result.Dependent
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.Navigable
import com.lambda.interaction.construction.result.Rank
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.task.tasks.BuildTask.Companion.breakBlock
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.awt.Color

/**
 * [PlaceResult] represents the result of a placement simulation.
 * Holds data about the placement and the result of the simulation.
 * Every [GenericResult] can [resolve] its own problem.
 * Every [GenericResult] can be compared to another [GenericResult].
 * First based on the context, then based on the [com.lambda.interaction.construction.result.Rank].
 */
sealed class PlaceResult : BuildResult() {
    override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"

    /**
     * Represents a successful placement. All checks have been passed.
     * @param context The context of the placement.
     */
    data class Place(
        override val pos: BlockPos,
        override val context: PlaceContext,
    ) : Contextual, Drawable, PlaceResult() {
        override val rank = Rank.PlaceSuccess

        override fun ShapeBuilder.buildRenderer() {
            with(context) { buildRenderer() }
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is Place -> context.compareTo(other.context)
                else -> super<Contextual>.compareResult(other)
            }
    }

    /**
     * Represents a placement result where the block placement does not meet integrity expectations.
     *
     * This class is used to provide details about a block placement issue in which the actual block
     * placed does not match the expected state, or additional integrity conditions are not met.
     *
     * @property pos The position of the block being inspected or placed.
     * @property expected The expected state of the block.
     * @property simulated The context of the item placement simulation.
     * @property actual The expected
     */
    data class NoIntegrity(
        override val pos: BlockPos,
        val expected: BlockState,
        val simulated: ItemPlacementContext,
        val actual: BlockState? = null,
    ) : Drawable, PlaceResult() {
        override val rank = Rank.PlaceNoIntegrity
        private val color = Color(252, 3, 3, 100)

        override fun ShapeBuilder.buildRenderer() {
            box(pos, expected, color, color)
        }
    }

    /**
     * Represents a scenario where block placement is obstructed by the player itself.
     *
     * @property pos The position of the block that was attempted to be placed.
     */
    data class BlockedBySelf(
        override val pos: BlockPos
    ) : Drawable, Navigable, PlaceResult() {
        override val rank = Rank.PlaceBlockedByPlayer
        private val color = Color(252, 3, 3, 100)
        override val goal = GoalInverted(GoalBlock(pos))

        override fun ShapeBuilder.buildRenderer() {
            box(pos, color, color)
        }
    }

    /**
     * Represents a scenario where block placement is obstructed by an entity.
     *
     * @property pos The position of the block that was attempted to be placed.
     */
    data class BlockedByEntity(
        override val pos: BlockPos,
        val entities: List<Entity>
    ) : Drawable, PlaceResult() {
        override val rank = Rank.PlaceBlockedByEntity
        private val color = Color(252, 3, 3, 100)

        override fun ShapeBuilder.buildRenderer() {
            entities.forEach { box(it, color) }
        }
    }

    /**
     * Represents a result indicating that a block cannot be replaced during a placement operation.
     *
     * @property pos The position of the block that cannot be replaced.
     * @property simulated The context of the item placement simulation.
     */
    data class CantReplace(
        override val pos: BlockPos,
        val simulated: ItemPlacementContext,
    ) : Resolvable, PlaceResult() {
        override val rank = Rank.PlaceCantReplace

        context(automated: Automated)
        override fun resolve() = automated.breakBlock(pos)
    }

    /**
     * Represents a placement result indicating that the scaffolding placement has exceeded the allowed limits.
     *
     * @property pos The position of the block where the placement attempt occurred.
     * @property simulated The context of the simulated item placement attempt.
     */
    data class ScaffoldExceeded(
        override val pos: BlockPos
    ) : PlaceResult() {
        override val rank = Rank.PlaceScaffoldExceeded
    }

    /**
     * Represents a result where a block placement operation was prevented because
     * the relevant block feature is disabled.
     *
     * @property pos The position of the block that could not be placed.
     * @property itemStack The item stack associated with the attempted placement.
     */
    data class BlockFeatureDisabled(
        override val pos: BlockPos,
        val itemStack: ItemStack,
    ) : PlaceResult() {
        override val rank = Rank.PlaceBlockFeatureDisabled
    }

    /**
     * Represents a result state where the placement or manipulation of a block resulted in an unexpected position.
     *
     * @property pos The intended position of the block.
     * @property actualPos The actual position of the block, which differs from the intended position.
     */
    data class UnexpectedPosition(
        override val pos: BlockPos,
        val actualPos: BlockPos,
    ) : PlaceResult() {
        override val rank = Rank.UnexpectedPosition
    }

    /**
     * Represents a result indicating an illegal usage during a placement operation.
     * E.g., the player can't modify the world or the block cannot be placed against the surface.
     *
     * @property pos The position of the block associated with the illegal usage result.
     * @property rank The ranking of this result, which is always `PLACE_ILLEGAL_USAGE`.
     */
    data class IllegalUsage(
        override val pos: BlockPos,
    ) : PlaceResult() {
        override val rank = Rank.PlaceIllegalUsage
    }

    data class Dependency(
        override val pos: BlockPos,
        override val dependency: BuildResult
    ) : PlaceResult(), Dependent by Dependent.Nested(dependency) {
        override val rank = lastDependency.rank
        override val compareBy = lastDependency
    }
}
