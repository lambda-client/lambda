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

object ProcessorRegistry : Loadable {
    private val processors = getInstances<PlacementProcessor>()
    private val processorCache = mutableMapOf<BlockState, PreprocessingInfo>()

    override fun load() = "Loaded ${processors.size} pre processors"

    fun TargetState.getProcessingInfo(): PreprocessingInfo =
        (this as? TargetState.State)?.let { state ->
            processorCache.getOrPut(state.blockState) {
                val infoAccumulator = PreprocessingInfoAccumulator()

                processors.forEach {
                    if (!it.acceptsState(state.blockState)) return@forEach
                    it.preProcess(state.blockState, infoAccumulator)
                }

                return infoAccumulator.complete()
            }
        } ?: PreprocessingInfo.DEFAULT
}
