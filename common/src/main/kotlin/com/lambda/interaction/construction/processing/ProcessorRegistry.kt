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

package com.lambda.interaction.construction.processing

import com.lambda.core.Loadable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.reflections.getInstances
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks

object ProcessorRegistry : Loadable {
    private const val PROCESSOR_PACKAGE = "com.lambda.interaction.construction.processing.processors"
    private val processors = getInstances<PlacementProcessor> { forPackages(PROCESSOR_PACKAGE) }
    private val processorCache = mutableMapOf<BlockState, PreprocessingStep>()

    override fun load() = "Loaded ${processors.size} pre processors"

    fun TargetState.findProcessorForState(): PreprocessingStep =
        (this as? TargetState.State)?.let { state ->
            processorCache.getOrPut(state.blockState) {
                (processors.find { it.acceptState(state.blockState) } ?: DefaultProcessor).preProcess(state.blockState)
            }
        } ?: DefaultProcessor.preProcess(Blocks.AIR.defaultState)

    object DefaultProcessor : PlacementProcessor() {
        override fun acceptState(state: BlockState) = true
        override fun preProcess(state: BlockState) = PreprocessingStep()
    }
}