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
import com.lambda.client.util.world.*
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
    var action = Action.UNINIT
    var context = Context.NONE

    enum class Context(val color: ColorHolder) {
        RESTOCK(ColorHolder()),
        LIQUID(ColorHolder()),
        NONE(ColorHolder())
    }

    // ToDo: Update actions inside PlaceBlock and BreakBlock
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
        runSafe { updateAction() }

        safeListener<TickEvent.ClientTickEvent> {
            updateAction()
        }
    }

    override fun SafeClientEvent.onInitialize() {
        updateAction(true)
    }

    private fun SafeClientEvent.updateAction(addActivities: Boolean = false) {
//        val owner = owner

//        if (owner !is BuildStructure) return

        val currentState = world.getBlockState(blockPos)

        when {
            /* is in desired state */
            currentState == targetState -> success()
            /* block needs to be placed */
            currentState.isLiquid ||
                (targetState != Blocks.AIR.defaultState && world.isPlaceable(blockPos, targetState.getCollisionBoundingBox(world, blockPos)
                    ?: AxisAlignedBB(blockPos))) -> {
                if (addActivities) {
                    val trueTarget = if (currentState.isLiquid) BuildTools.defaultFillerMat.defaultState else targetState

                    addSubActivities(
                        PlaceBlock(blockPos, trueTarget, BuildTools.doPending)
                    )
                } else {
                    action = when {
                        getNeighbour(blockPos, 1, BuildTools.maxReach, true) != null -> Action.PLACE
                        getNeighbour(blockPos, 1, 256f, false) != null -> Action.WRONG_POS_PLACE
                        else -> Action.INVALID_PLACE
                    }
                }
            }
            /* should be ignored */
            respectIgnore && currentState.block in ignoredBlocks -> success()
            /* only option left is breaking the block */
            else -> {
                if (addActivities) {
                    addSubActivities(
                        BreakBlock(blockPos, BuildTools.doPending)
                    )
                } else {
                    action = when {
                        getMiningSide(blockPos, BuildTools.maxReach) != null -> Action.BREAK
                        getMiningSide(blockPos) != null -> Action.WRONG_POS_BREAK
                        else -> Action.INVALID_BREAK
                    }
                }
            }
        }

        toRender.clear()
        action.addToRenderer(this@BuildBlock)
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        status = Status.UNINITIALIZED
    }

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childException !is BreakBlock.NoExposedSideFound) return false

        status = Status.UNINITIALIZED
        return true
    }
}