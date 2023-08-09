package com.lambda.client.activity.activities.construction

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalXZ
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.activity.activities.storage.types.Area
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

class ClearArea(
    private val area: Area,
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
        if (player.flooredPosition !in area.containedBlocks) {
            val highestNonAirBlock = world.getTopSolidOrLiquidBlock(area.center)

            MessageSendHelper.sendWarningMessage("Player is not in the area $area! Moving to closest position (${highestNonAirBlock.asString()})...")
            addSubActivities(CustomGoal(GoalBlock(highestNonAirBlock),
                inGoal = { blockPos -> area.containedBlocks.contains(blockPos) }, timeout = 999999L))
            status = Status.UNINITIALIZED
            return
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
                    BuildStructure(structure.toMap(), collectAll = collectAll, allowBreakDescend = true)
                )
                structure.clear()
            }
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is CustomGoal) return

        status = Status.UNINITIALIZED
    }
}