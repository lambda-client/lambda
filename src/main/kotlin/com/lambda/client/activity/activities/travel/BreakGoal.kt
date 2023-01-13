package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getMiningSide
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

class BreakGoal(
    private val blockPos: BlockPos,
    override val timeout: Long = 60000L
) : TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        if (isInBlockAABB(blockPos)
            && GoalNear(blockPos, 3).isInGoal(player.flooredPosition)) success()
    }

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            if (isInBlockAABB(blockPos.up())) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalInverted(GoalBlock(blockPos.up())))
                return@safeListener
            }

            getMiningSide(blockPos, BuildTools.maxReach)?.let {
                success()
            } ?: run {
                val goalNear = GoalNear(blockPos, 3)

                if (!goalNear.isInGoal(player.flooredPosition)) {
                    BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goalNear)
                    return@safeListener
                }

                failedWith(NoPathToBreakFound())
            }
        }
    }

    private fun SafeClientEvent.isInBlockAABB(blockPos: BlockPos): Boolean {
        return !world.checkNoEntityCollision(AxisAlignedBB(blockPos), null)
    }

    class NoPathToBreakFound : Exception("No path to break position found (scaffolding not yet implemented)")
}