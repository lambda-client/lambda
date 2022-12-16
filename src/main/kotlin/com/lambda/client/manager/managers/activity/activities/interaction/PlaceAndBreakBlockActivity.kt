package com.lambda.client.manager.managers.activity.activities.interaction

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrMoveToItemActivity
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.items.item
import com.lambda.client.util.math.Direction
import net.minecraft.block.Block
import net.minecraft.init.Items
import net.minecraft.util.math.BlockPos

class PlaceAndBreakBlockActivity(private val block: Block) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        subActivities.add(SwapOrMoveToItemActivity(block.item))
        subActivities.add(PlaceBlockActivity(
            BlockPos(player.flooredPosition.add(Direction.fromEntity(player).directionVec)),
            block
        ))
        subActivities.add(SwapOrMoveToItemActivity(Items.DIAMOND_PICKAXE))
        subActivities.add(BreakBlockActivity(
            BlockPos(player.flooredPosition.add(Direction.fromEntity(player).directionVec))
        ))
    }
}