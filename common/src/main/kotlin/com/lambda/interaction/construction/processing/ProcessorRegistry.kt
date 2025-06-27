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
import net.minecraft.state.property.Properties

object ProcessorRegistry : Loadable {
    private val processors = getInstances<PlacementProcessor>()
    private val processorCache = mutableMapOf<BlockState, PreProcessingInfo>()

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
    )

    override fun load() = "Loaded ${processors.size} pre processors"

    fun TargetState.getProcessingInfo(): PreProcessingInfo =
        (this as? TargetState.State)?.let { state ->
            processorCache.getOrPut(state.blockState) {
                val infoAccumulator = PreProcessingInfoAccumulator()

                processors.forEach {
                    if (!it.acceptsState(state.blockState)) return@forEach
                    it.preProcess(state.blockState, infoAccumulator)
                }

                return infoAccumulator.complete()
            }
        } ?: PreProcessingInfo.DEFAULT
}
