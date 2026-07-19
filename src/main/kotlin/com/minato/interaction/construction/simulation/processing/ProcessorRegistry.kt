
package com.minato.interaction.construction.simulation.processing

import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.core.Loadable
import com.minato.interaction.construction.simulation.SimDsl
import com.minato.interaction.construction.verify.TargetState
import com.minato.util.BlockUtils.matches
import com.minato.util.ReflectionUtils.getInstances
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import java.util.*

object ProcessorRegistry : Loadable {
	private val stateProcessors = getInstances<StateProcessor>()
	private val propertyPreProcessors = getInstances<PropertyPreProcessor>()
	private val propertyPostProcessors = getInstances<PropertyPostProcessor>()
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
		Properties.NOTE
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
		val targetBlockState = targetState.getState(pos)
		val processorCacheKey = state to targetBlockState
		val preProcessingInfo = processorCache.getOrElse(processorCacheKey) {
			preProcess(pos, state, targetBlockState, targetState.getStack(pos)).also { info ->
				if (info?.noCaching != true) processorCache[processorCacheKey] = info
			}
		} ?: return null

		return PreProcessingData(preProcessingInfo, pos)
	}

	context(safeContext: SafeContext)
	private fun preProcess(pos: BlockPos, state: BlockState, targetState: BlockState, itemStack: ItemStack) =
		PreProcessingInfoAccumulator(targetState, itemStack.item).run {
			var stateProcessing = false
			stateProcessors.forEach { processor ->
				if (processor.acceptsState(state, targetState)) {
					with(processor) { preProcess(state, targetState, pos) }
					stateProcessing = true
				}
			}
			if (!omitInteraction) {
				if (state.block != expectedState.block) {
					if (state.isReplaceable)  {
						propertyPreProcessors.forEach { processor ->
							if (processor.acceptsState(state, expectedState)) {
								with(processor) { preProcess(state, expectedState, pos) }
							}
						}
					} else if (!stateProcessing) return@run null
				} else {
					propertyPostProcessors.forEach { processor ->
						if (processor.acceptsState(state, expectedState)) {
							with(processor) { preProcess(state, expectedState, pos) }
						}
					}
					if (!state.matches(expectedState, ignore) && !stateProcessing) return@run null
				}
			}
			complete()
		}
}

data class PreProcessingData(val info: PreProcessingInfo, val pos: BlockPos)
