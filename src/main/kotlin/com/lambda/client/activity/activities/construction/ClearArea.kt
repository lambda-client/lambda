package com.lambda.client.activity.activities.construction

import baritone.api.pathing.goals.GoalXZ
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

class ClearArea(
    private val area: WorldEater.Area,
    private val layerSize: Int = 1,
    private val sliceSize: Int = 1,
    private val sliceDirection: EnumFacing = EnumFacing.NORTH,
    private val collectAll: Boolean = false,
    override val aabbCompounds: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : RenderAABBActivity, Activity() {

    init {
        RenderAABBActivity.Companion.RenderAABB(
            AxisAlignedBB(
                area.minX.toDouble(), area.minY.toDouble(), area.minZ.toDouble(),
                (area.maxX + 1).toDouble(), (area.maxY + 1).toDouble(), (area.maxZ + 1).toDouble()
            ),
            ColorHolder(245, 66, 66)
        ).also { aabbCompounds.add(it) }
    }

    override fun SafeClientEvent.onInitialize() {
        with(area) {
            if (!playerInArea) {
                MessageSendHelper.sendWarningMessage("You are not in the area!")
                addSubActivities(CustomGoal(GoalXZ(center.x, center.z)))
                return@onInitialize
            }
        }

        val layers = (area.minY..area.maxY).reversed()

        val structure = mutableMapOf<BlockPos, IBlockState>()

        layers.forEach { y ->
            if (y !in 0..world.actualHeight) return@forEach

            (area.minX..area.maxX).forEach { x ->
                (area.minZ..area.maxZ).forEach { z ->
                    structure[BlockPos(x, y, z)] = Blocks.AIR.defaultState
                }
            }

            if (y.mod(layerSize) == 0 || y == layers.last) {
                addSubActivities(
                    BuildStructure(structure.toMap(), collectAll = collectAll)
                )
                structure.clear()
            }
        }
    }
}