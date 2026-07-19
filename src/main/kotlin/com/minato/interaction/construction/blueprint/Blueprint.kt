
package com.minato.interaction.construction.blueprint

import com.minato.interaction.construction.verify.TargetState
import com.minato.util.collections.updatableLazy
import com.minato.util.extension.Structure
import com.minato.util.math.roundedBlockPos
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.math.BlockBox
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

abstract class Blueprint {
    abstract val structure: Structure

    val bounds = updatableLazy {
        if (structure.isEmpty()) return@updatableLazy null
        val maxX = structure.keys.maxOf { it.x }
        val maxY = structure.keys.maxOf { it.y }
        val maxZ = structure.keys.maxOf { it.z }
        val minX = structure.keys.minOf { it.x }
        val minY = structure.keys.minOf { it.y }
        val minZ = structure.keys.minOf { it.z }
        BlockBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun getClosestPointTo(target: Vec3d): Vec3d {
        val bounds = bounds.value ?: return target
        val d = MathHelper.clamp(target.x, bounds.minX.toDouble(), bounds.maxX.toDouble())
        val e = MathHelper.clamp(target.y, bounds.minY.toDouble(), bounds.maxY.toDouble())
        val f = MathHelper.clamp(target.z, bounds.minZ.toDouble(), bounds.maxZ.toDouble())
        return Vec3d(d, e, f)
    }

    fun isOutOfBounds(vec3d: Vec3d): Boolean = bounds.value?.contains(vec3d.roundedBlockPos) == false

    val center get() = bounds.value?.center

    companion object {
        fun emptyStructure(): Structure = emptyMap()

        fun BlockBox.toStructure(targetState: TargetState): Structure =
            BlockPos.stream(this).toList().associateWith { targetState }

        fun BlockPos.toStructure(targetState: TargetState): Structure =
            setOf(this).associateWith { targetState }

        fun StructureTemplate.toStructure(): Structure =
            blockInfoLists
                .flatMap { it.all }
                .associate { it.pos to TargetState.State(it.state) }
    }
}
