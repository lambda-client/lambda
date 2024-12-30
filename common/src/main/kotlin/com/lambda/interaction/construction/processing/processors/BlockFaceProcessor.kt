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

package com.lambda.interaction.construction.processing.processors

import com.lambda.interaction.construction.processing.PlacementProcessor
import com.lambda.interaction.construction.processing.PreprocessingStep
import net.minecraft.block.BlockState
import net.minecraft.block.enums.BlockFace
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction
import kotlin.jvm.optionals.getOrNull

object BlockFaceProcessor : PlacementProcessor() {
    override fun acceptState(state: BlockState) =
        state.getOrEmpty(Properties.BLOCK_FACE).isPresent

    override fun preProcess(state: BlockState): PreprocessingStep {
        val property = state.getOrEmpty(Properties.BLOCK_FACE).getOrNull() ?: return PreprocessingStep()
        val sides = when (property) {
            BlockFace.FLOOR -> setOf(Direction.DOWN)
            BlockFace.CEILING -> setOf(Direction.UP)
            BlockFace.WALL -> Direction.Type.HORIZONTAL.stream().toList().toSet()
        }
        return PreprocessingStep(sides = sides)
    }
}