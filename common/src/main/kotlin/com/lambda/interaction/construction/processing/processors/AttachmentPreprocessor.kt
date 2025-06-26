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

package com.lambda.interaction.construction.processing.processors

import com.lambda.interaction.construction.processing.PlacementProcessor
import com.lambda.interaction.construction.processing.PreprocessingInfoAccumulator
import net.minecraft.block.BlockState
import net.minecraft.block.enums.Attachment
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object AttachmentPreprocessor : PlacementProcessor() {
    override fun acceptsState(state: BlockState) =
        state.properties.contains(Properties.ATTACHMENT)

    override fun preProcess(state: BlockState, accumulator: PreprocessingInfoAccumulator) {
        val attachment = state.get(Properties.ATTACHMENT) ?: return
        with (accumulator) {
            when (attachment) {
                Attachment.FLOOR -> retainSides(Direction.DOWN)
                Attachment.CEILING -> retainSides(Direction.UP)
                else -> retainSides { Direction.Type.HORIZONTAL.contains(it) }
            }
        }
    }
}