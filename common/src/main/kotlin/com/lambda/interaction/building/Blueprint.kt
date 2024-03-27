package com.lambda.interaction.building

import com.lambda.interaction.building.verify.TargetState
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

data class Blueprint(
    private val structure: Map<BlockPos, TargetState>,
    var offset: BlockPos? = null,
) {
    val origin: BlockPos
        get() = offset ?: structure.keys.firstOrNull() ?: BlockPos.ORIGIN

    private val modifications = mutableMapOf<BlockPos, TargetState>()
    val structureMap: Map<BlockPos, TargetState>
        get() {
            offset?.let {
                val offsetMap = mutableMapOf<BlockPos, TargetState>()
                for ((pos, state) in structure) {
                    offsetMap[pos.add(it)] = state
                }
                return offsetMap + modifications
            }

            return structure + modifications
        }

    companion object {
        fun Box.fromBox(targetState: TargetState) =
            Blueprint(BlockPos.stream(this).map { BlockPos(it) }.toList().associateWith { targetState })

        fun BlockBox.fromBlockBox(targetState: TargetState) =
            Blueprint(BlockPos.stream(this).map { BlockPos(it) }.toList().associateWith { targetState })

        fun BlockPos.fromBlockPos(targetState: TargetState) =
            Blueprint(setOf(this).associateWith { targetState })

//        fun Schematic.fromSchematic() =
//            Blueprint(this.blockMap.map { it.key to TargetState.BlockState(it.value) }.toMap())

        fun StructureTemplate.fromStructureTemplate() =
            Blueprint(
                blockInfoLists
                    .flatMap { it.all }
                    .associate { it.pos to TargetState.State(it.state) }
            )
    }
}
