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

package com.lambda.interaction.construction.simulation.processing

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfo.Companion.default
import com.lambda.interaction.construction.simulation.SimDsl
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.reflections.getInstances
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import java.util.*

object ProcessorRegistry : Loadable{
	private val stateProcessors = getInstances<com.lambda.interaction.construction.simulation.processing.StateProcessor>()
    private val propertyPreProcessors = getInstances<com.lambda.interaction.construction.simulation.processing.PropertyPreProcessor>()
	private val propertyPostProcessors = getInstances<com.lambda.interaction.construction.simulation.processing.PropertyPostProcessor>()
    private val processorCache = Collections.synchronizedMap<Pair<BlockState, BlockState>, PreProcessingInfo?>(mutableMapOf())

    /**
     * List of properties that can be processed after the block is placed. This is often used to ignore these properties
     * when placing blocks, as sometimes they can only be set to the right state after placement.
     */
    val postProcessedProperties = setOf(
        Properties.EXTENDED,
        Properties.EYE,
        Properties.HAS_BOOK,
        Properties.HAS_BOTTLE_0, Properties.HAS_BOTTLE_1, Properties.HAS_BOTTLE_2,
        Properties.HAS_RECORD,
        Properties.INVERTED,
        Properties.LIT,
        Properties.LOCKED,
        Properties.OCCUPIED,
        Properties.OPEN,
        Properties.POWERED,
        Properties.SIGNAL_FIRE,
        Properties.SNOWY,
        Properties.TRIGGERED,
        Properties.UNSTABLE,
        Properties.WATERLOGGED,
        Properties.BERRIES,
        Properties.BLOOM,
        Properties.SHRIEKING,
        Properties.CAN_SUMMON,
        Properties.FLOWER_AMOUNT,
        Properties.EAST_WALL_SHAPE, Properties.SOUTH_WALL_SHAPE, Properties.WEST_WALL_SHAPE, Properties.NORTH_WALL_SHAPE,
        Properties.EAST_WIRE_CONNECTION, Properties.SOUTH_WIRE_CONNECTION, Properties.WEST_WIRE_CONNECTION, Properties.NORTH_WIRE_CONNECTION,
        Properties.AGE_1,
        Properties.AGE_2,
        Properties.AGE_3,
        Properties.AGE_4,
        Properties.AGE_5,
        Properties.AGE_7,
        Properties.AGE_15,
        Properties.AGE_25,
        Properties.BITES,
        Properties.CANDLES,
        Properties.DELAY,
        Properties.EGGS,
        Properties.HATCH,
        Properties.LAYERS,
        Properties.LEVEL_3,
        Properties.LEVEL_8,
        Properties.LEVEL_1_8,
        Properties.HONEY_LEVEL,
        Properties.LEVEL_15,
        Properties.MOISTURE,
        Properties.NOTE,
        Properties.PICKLES,
        Properties.POWER,
        Properties.STAGE,
        Properties.CHARGES,
        Properties.COMPARATOR_MODE,
        Properties.INSTRUMENT,
        Properties.STAIR_SHAPE,
        Properties.TILT,
        Properties.THICKNESS,
        Properties.SCULK_SENSOR_PHASE,
        Properties.SLOT_0_OCCUPIED, Properties.SLOT_1_OCCUPIED, Properties.SLOT_2_OCCUPIED, Properties.SLOT_3_OCCUPIED, Properties.SLOT_4_OCCUPIED, Properties.SLOT_5_OCCUPIED,
        Properties.DUSTED,
        Properties.CRAFTING,
        Properties.TRIAL_SPAWNER_STATE,
        Properties.DISARMED,
        Properties.ATTACHED,
        Properties.DRAG,
        Properties.ENABLED,
        Properties.IN_WALL,
        Properties.UP,
        Properties.DOWN,
        Properties.NORTH,
        Properties.EAST,
        Properties.SOUTH,
        Properties.WEST,
        Properties.PERSISTENT,
        Properties.DISTANCE_1_7
    )

	val standardInteractProperties = setOf(
		Properties.INVERTED,
		Properties.DELAY,
		Properties.COMPARATOR_MODE,
		Properties.OPEN,
		Properties.NOTE,

	)

    override fun load() = "Loaded ${propertyPreProcessors.size} pre processors"

    /**
     * [PreProcessingInfo]'s are cached to avoid duplicate computations as block states are immutable.
     *
     * @return A [PreProcessingInfo] object containing information about the block state. This method runs through
     * each pre-processor checking if the state can be accepted. If so, the state is passed through the pre-processor
     * which can call the functions within the [PreProcessingInfoAccumulator] DSL to modify the information.
     */
    @SimDsl
    fun AutomatedSafeContext.getProcessingInfo(state: BlockState, targetState: TargetState, pos: BlockPos): PreProcessingData? {
        val targetBlockState = (targetState as? TargetState.State)?.blockState
	        ?: return PreProcessingData(default(targetState, pos), pos)

	    val processorCacheKey = state to targetBlockState
        val preProcessingInfo = processorCache.getOrElse(processorCacheKey) {
			preProcess(pos, state, targetBlockState, targetState.getStack(pos)).also { info ->
				if (info?.noCaching != true) processorCache[processorCacheKey] = info
			}
        }

        return PreProcessingData(preProcessingInfo ?: return null, pos)
    }

	context(safeContext: SafeContext)
	private fun preProcess(pos: BlockPos, state: BlockState, targetState: BlockState, itemStack: ItemStack) =
		PreProcessingInfoAccumulator(targetState, itemStack.item).run {
			val stateProcessing = stateProcessors.any { processor ->
				processor.acceptsState(state, targetState).also { accepted ->
					if (accepted)
						with(processor) { preProcess(state, targetState, pos) }
				}
			}
			if (omitPlacement) return@run complete()
			if (!stateProcessing) {
				if (!state.isReplaceable && state.block != expectedState.block) return@run null
				if (state.block != expectedState.block) propertyPreProcessors.forEach { processor ->
					if (processor.acceptsState(targetState))
						with(processor) { preProcess(state, expectedState) }
				} else propertyPostProcessors.forEach { processor ->
					if (processor.acceptsState(state, expectedState))
						with(processor) { preProcess(state, expectedState) }
				}
			}
			complete()
		}
}

data class PreProcessingData(val info: PreProcessingInfo, val pos: BlockPos)
