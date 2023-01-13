package com.lambda.client.activity.activities.highlevel

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.ignoredBlocks
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getMiningSide
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.isPlaceable
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

class BuildBlock(
    val blockPos: BlockPos,
    private val targetState: IBlockState,
    private val respectIgnore: Boolean = false,
    override val maxAttempts: Int = 3,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf()
) : AttemptActivity, RenderAABBActivity, Activity() {
    var currentAction = Action.UNINIT

    enum class Action(val color: ColorHolder) {
        BREAK(ColorHolder(222, 0, 0)),
        PLACE(ColorHolder(35, 188, 254)),
        WRONG_POS_BREAK(ColorHolder(112, 0, 0)),
        WRONG_POS_PLACE(ColorHolder(20, 108, 145)),
        INVALID_BREAK(ColorHolder(46, 0, 0)),
        INVALID_PLACE(ColorHolder(11, 55, 74)),
        UNINIT(ColorHolder(11, 11, 11));

        fun addToRenderer(activity: BuildBlock) {
            with(activity) {
                toRender.add(RenderAABBActivity.Companion.RenderBlockPos(
                    blockPos,
                    color
                ))
            }
        }
    }

    init {
        runSafe { updateState() }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()
        }
    }

    override fun SafeClientEvent.onInitialize() {
        updateState(true)
    }

    private fun SafeClientEvent.updateState(addActivities: Boolean = false) {
        val currentState = world.getBlockState(blockPos)

        when {
            /* is in desired state */
            currentState.block == targetState.block -> success()
            /* block needs to be placed */
            targetState.block != Blocks.AIR && world.isPlaceable(blockPos, targetState.getCollisionBoundingBox(world, blockPos)
                ?: AxisAlignedBB(blockPos)) -> {
                if (addActivities) {
                    addSubActivities(
                        PlaceBlock(blockPos, targetState, doPending = true)
                    )
                } else {
                    if (getNeighbour(blockPos, 1, BuildTools.maxReach, true) != null) {
                        currentAction = Action.PLACE
                    } else {
                        getNeighbour(blockPos, 1, 256f, false)?.let {
                            currentAction = Action.WRONG_POS_PLACE
                        } ?: run {
                            currentAction = Action.INVALID_PLACE
                        }
                    }
                }
            }
            /* should be ignored */
            respectIgnore && currentState.block in ignoredBlocks -> success()
            /* only option left is breaking the block */
            else -> {
                if (addActivities) {
                    addSubActivities(
                        BreakBlock(blockPos, doPending = true)
                    )
                } else {
                    getMiningSide(blockPos, BuildTools.maxReach)?.let {
                        currentAction = Action.BREAK
                    } ?: run {
                        getMiningSide(blockPos)?.let {
                            currentAction = Action.WRONG_POS_BREAK
                        } ?: run {
                            currentAction = Action.INVALID_BREAK
                        }
                    }
                }
            }
        }

        toRender.clear()
        currentAction.addToRenderer(this@BuildBlock)
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        activityStatus = ActivityStatus.UNINITIALIZED
    }
}