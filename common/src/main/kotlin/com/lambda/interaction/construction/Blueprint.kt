package com.lambda.interaction.construction

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.primitives.extension.Structure
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

abstract class Blueprint {
    abstract val structure: Structure

    open fun isDone(safeContext: SafeContext) =
        structure.all { (pos, targetState) ->
            with(safeContext) {
                targetState.matches(pos.blockState(world), pos, world)
            }
        }

    companion object {
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
