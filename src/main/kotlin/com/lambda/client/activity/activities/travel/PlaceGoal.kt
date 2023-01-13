package com.lambda.client.activity.activities.travel

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalInverted
import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

class PlaceGoal(
    private val blockPos: BlockPos,
    override val timeout: Long = 60000L
) : TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        getNeighbour(blockPos, attempts = 1, visibleSideCheck = true, range = 4.5f)?.let {
            success()
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            if (isInBlockAABB(blockPos)) {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalInverted(GoalBlock(blockPos)))
                return@safeListener
            }

            getNeighbour(blockPos, attempts = 1, visibleSideCheck = true, range = 4.5f)?.let {
                success()
            } ?: run {
                getNeighbour(blockPos, attempts = 1, range = 256f)?.let {
                    val goalNear = GoalNear(blockPos, 3)

                    if (!goalNear.isInGoal(player.flooredPosition)) {
                        BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(goalNear)
                        return@safeListener
                    }

                    BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalNear(blockPos.offset(it.side), 1))
                } ?: run {
                    // ToDo: Scaffolding!

                    failedWith(NoPathToPlaceFound())
                }
            }
        }
    }

    private fun SafeClientEvent.isInBlockAABB(blockPos: BlockPos) =
        !world.checkNoEntityCollision(AxisAlignedBB(blockPos), null)

    class NoPathToPlaceFound : Exception("No path to place position found (scaffolding not yet implemented)")
}