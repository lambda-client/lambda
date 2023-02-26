package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

class BreakArea(
    private val pos1: BlockPos,
    private val pos2: BlockPos,
    private val layerSize: Int = 1,
    private val sliceSize: Int = 1,
    private val sliceDirection: EnumFacing = EnumFacing.NORTH,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(
        RenderAABBActivity.Companion.RenderBlockPos(pos1, ColorHolder(255, 0, 255)),
        RenderAABBActivity.Companion.RenderBlockPos(pos2, ColorHolder(0, 255, 0))
    )
) : RenderAABBActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val minX = minOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxX = maxOf(pos1.x, pos2.x)
        val maxY = maxOf(pos1.y, pos2.y)
        val maxZ = maxOf(pos1.z, pos2.z)

        val layers = (minY..maxY).reversed()

        layers.forEach { y ->
            val structure = mutableMapOf<BlockPos, IBlockState>()

            (minX..maxX).forEach { x ->
                (minZ..maxZ).forEach { z ->
                    structure[BlockPos(x, y, z)] = Blocks.AIR.defaultState
                }
            }

            if (y.mod(layerSize) == 0 || y == layers.last) {
                addSubActivities(
                    BuildStructure(structure)
                )
            }
        }
    }
}