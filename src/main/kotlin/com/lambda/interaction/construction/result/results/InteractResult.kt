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

import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.ComparableResult
import com.lambda.interaction.construction.result.Contextual
import com.lambda.interaction.construction.result.Dependent
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.Rank
import net.minecraft.util.math.BlockPos

sealed class InteractResult : BuildResult() {
    override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"

    data class Interact(
        override val pos: BlockPos,
        override val context: InteractionContext
    ) : Contextual, Drawable, InteractResult() {
        override val rank = Rank.InteractSuccess

        override fun ShapeBuilder.buildRenderer() {
            with(context) { buildRenderer() }
        }

        override fun compareResult(other: ComparableResult<Rank>) =
            when (other) {
                is Interact -> context.compareTo(other.context)
                else -> super<Contextual>.compareResult(other)
            }
    }

    data class Dependency(
        override val pos: BlockPos,
        override val dependency: BuildResult
    ) : InteractResult(), Dependent by Dependent.Nested(dependency) {
        override val rank = dependency.rank
        override val compareBy = lastDependency
    }
}
