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

package com.lambda.interaction.construction

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.extension.Structure
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

abstract class Blueprint {
    abstract val structure: Structure

    open fun isDone(ctx: SafeContext) =
        structure.all { (pos, targetState) ->
            with(ctx) {
                val state = pos.blockState(world)
                targetState.matches(state, pos, world) || state.block in TaskFlow.ignoredBlocks
            }
        }

    companion object {
        fun emptyStructure(): Structure = emptyMap()

        fun Box.toStructure(targetState: TargetState): Structure =
            BlockPos.stream(this)
                .map { it.blockPos }
                .toList()
                .associateWith { targetState }

        fun BlockBox.toStructure(targetState: TargetState): Structure =
            BlockPos.stream(this)
                .map { it.blockPos }
                .toList()
                .associateWith { targetState }

        fun BlockPos.toStructure(targetState: TargetState): Structure =
            setOf(this)
                .associateWith { targetState }

//        fun Schematic.fromSchematic() =
//            this.blockMap.map { it.key to TargetState.BlockState(it.value) }.toMap()

        fun StructureTemplate.toStructure(): Structure =
            blockInfoLists
                .flatMap { it.all }
                .associate { it.pos to TargetState.State(it.state) }
    }
}
