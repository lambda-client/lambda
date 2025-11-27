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

package com.lambda.interaction.construction.processing

import com.lambda.core.Loadable
import com.lambda.interaction.construction.processing.ProcessorRegistry.IntermediaryInfo.Companion.intermediaryInfo
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.item
import com.lambda.util.reflections.getInstances
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.FlowerPotBlock
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.*

object ProcessorRegistry : Loadable {
    private val processors = getInstances<PlacementProcessor>()
    private val processorCache = Collections.synchronizedMap<BlockState, PreProcessingInfo?>(mutableMapOf())

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
        Properties.CHEST_TYPE,
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

    /**
     * Map of blocks that get placed as a different [Block] type, to then be updated afterward. Bamboo and potted flowers are
     * two examples.
     *
     * @see IntermediaryInfo
     */
    val intermediaryBlockMap = buildMap<Block, IntermediaryInfo> {
        this[Blocks.BAMBOO] = intermediaryInfo(IntermediaryProcess(Blocks.BAMBOO_SAPLING, item = Items.BAMBOO))
        BlockUtils.pottedBlocks.forEach {
            this[it] = intermediaryInfo(
                IntermediaryProcess(Blocks.FLOWER_POT, item = Items.FLOWER_POT),
                IntermediaryProcess(Blocks.FLOWER_POT, it, (it as FlowerPotBlock).content.item)
            )
        }
    }

    override fun load() = "Loaded ${processors.size} pre processors"

    /**
     * [PreProcessingInfo]'s are cached to avoid duplicate computations as block states are immutable.
     *
     * @return A [PreProcessingInfo] object containing information about the block state. This method runs through
     * each pre-processor checking if the state can be accepted. If so, the state is passed through the pre-processor
     * which can call the functions within the [PreProcessingInfoAccumulator] DSL to modify the information.
     */
    fun TargetState.getProcessingInfo(pos: BlockPos): PreProcessingData? {
        if (this !is TargetState.State) return PreProcessingData(PreProcessingInfo.DEFAULT, pos)

        val get: () -> PreProcessingInfo? = get@{
            val infoAccumulator = PreProcessingInfoAccumulator()

            processors.forEach { processor ->
                if (!processor.acceptsState(blockState)) return@forEach
                processor.preProcess(blockState, pos, infoAccumulator)
            }

            infoAccumulator.complete()
        }
        val preProcessingInfo = processorCache.getOrPut(blockState, get) ?: return null
        return PreProcessingData(preProcessingInfo, pos)
    }

    /**
     * Contains the starting initial block placement and any subsequent intermediary processes to transform the placement
     * into the final block.
     *
     * @see IntermediaryProcess
     */
    data class IntermediaryInfo private constructor(
        val startBlock: IntermediaryProcess,
        val intermediaryProcesses: List<IntermediaryProcess> = emptyList(),
    ) {
        fun getIntermediaryProcess(state: BlockState) = intermediaryProcesses.firstOrNull { it.block === state.block }
        fun isIntermediaryBlock(state: BlockState) = intermediaryProcesses.any {
            it.block === state.block || it.targetBlock === state.block
        } || startBlock.targetBlock === state.block

        companion object {
            fun intermediaryInfo(
                startingBlock: IntermediaryProcess,
                vararg intermediaryProcesses: IntermediaryProcess
            ) = IntermediaryInfo(startingBlock, intermediaryProcesses.toList())
        }
    }

    /**
     * Holds the required information for placing a [Block] with more than one placement to achieve its target.
     *
     * The use of [Block] instead of [BlockState] here is intentional as we would only have to alter the [Properties]s
     * if the placement was the correct [BlockState]. This is only used when one [Block] needs to transform into another.
     */
    data class IntermediaryProcess(
        val block: Block,
        val targetBlock: Block = block,
        val item: Item,
        val sides: Set<Direction> = Direction.entries.toSet()
    )
}

data class PreProcessingData(val info: PreProcessingInfo, val pos: BlockPos)
