package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

class ClearArea(
    pos1: BlockPos,
    pos2: BlockPos,
    private val layerSize: Int = 1,
    private val sliceSize: Int = 1,
    private val sliceDirection: EnumFacing = EnumFacing.NORTH,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : RenderAABBActivity, Activity() {
    private val minX = minOf(pos1.x, pos2.x)
    private val minY = minOf(pos1.y, pos2.y)
    private val minZ = minOf(pos1.z, pos2.z)
    private val maxX = maxOf(pos1.x, pos2.x)
    private val maxY = maxOf(pos1.y, pos2.y)
    private val maxZ = maxOf(pos1.z, pos2.z)

    init {
        RenderAABBActivity.Companion.RenderAABB(
            AxisAlignedBB(
                minX.toDouble(), minY.toDouble(), minZ.toDouble(),
                (maxX + 1).toDouble(), (maxY + 1).toDouble(), (maxZ + 1).toDouble()
            ),
            ColorHolder(245, 66, 66)
        ).also { toRender.add(it) }
    }

    override fun SafeClientEvent.onInitialize() {
        val layers = (minY..maxY).reversed()

        val structure = mutableMapOf<BlockPos, IBlockState>()

        layers.forEach { y ->
            (minX..maxX).forEach { x ->
                (minZ..maxZ).forEach { z ->
                    structure[BlockPos(x, y, z)] = Blocks.AIR.defaultState
                }
            }

            if (y.mod(layerSize) == 0 || y == layers.last) {
                addSubActivities(
                    BuildStructure(structure.toMap())
                )
                structure.clear()
            }
        }
    }
}