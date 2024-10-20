package com.lambda.interaction.construction.blueprint

import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.extension.Structure
import com.lambda.util.math.VecUtils.blockPos
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.math.*

abstract class Blueprint {
    abstract val structure: Structure

    private val bounds: BlockBox by lazy {
        val maxX = structure.keys.maxOf { it.x }
        val maxY = structure.keys.maxOf { it.y }
        val maxZ = structure.keys.maxOf { it.z }
        val minX = structure.keys.minOf { it.x }
        val minY = structure.keys.minOf { it.y }
        val minZ = structure.keys.minOf { it.z }
        BlockBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun getClosestPointTo(target: Vec3d): Vec3d {
        val d = MathHelper.clamp(target.x, bounds.minX.toDouble(), bounds.maxX.toDouble())
        val e = MathHelper.clamp(target.y, bounds.minY.toDouble(), bounds.maxY.toDouble())
        val f = MathHelper.clamp(target.z, bounds.minZ.toDouble(), bounds.maxZ.toDouble())
        return Vec3d(d, e, f)
    }

    fun isOutOfBounds(vec3d: Vec3d): Boolean = !bounds.contains(vec3d.blockPos)

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
