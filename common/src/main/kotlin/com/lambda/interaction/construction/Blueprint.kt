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
