package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

class WorldEater(
    private val pos1: BlockPos,
    private val pos2: BlockPos
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val minX = minOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxX = maxOf(pos1.x, pos2.x)
        val maxY = maxOf(pos1.y, pos2.y)
        val maxZ = maxOf(pos1.z, pos2.z)

        (minY..maxY).reversed().forEach { y ->
            val structure = mutableMapOf<BlockPos, IBlockState>()

            (minX..maxX).forEach { x ->
                (minZ..maxZ).forEach { z ->
                    structure[BlockPos(x, y, z)] = Blocks.AIR.defaultState
                }
            }

            addSubActivities(
                BuildStructure(structure)
            )
        }
    }
}