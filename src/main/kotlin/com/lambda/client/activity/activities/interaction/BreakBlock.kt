package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.travel.BreakGoal
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.*
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import com.lambda.client.util.world.isLiquid
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.nio.channels.AcceptPendingException
import java.util.*
import kotlin.math.ceil

class BreakBlock(
    private val blockPos: BlockPos,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1,
    override var timeout: Long = 200L,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override var rotation: Vec2f? = null,
    override var context: BuildActivity.BuildContext = BuildActivity.BuildContext.NONE,
    override var action: BuildActivity.BuildAction = BuildActivity.BuildAction.UNINIT,
    override var hitVec: Vec3d = Vec3d.ZERO
) : TimeoutActivity, AttemptActivity, RotatingActivity, RenderAABBActivity, BuildActivity, Activity() {
    private var side: EnumFacing? = null
    private var ticksNeeded = 0
    private var initState = Blocks.AIR.defaultState
    private var drop: Item = Items.AIR

    private val renderActivity = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, action.color
    ).also { toRender.add(it) }

    init {
        runSafe { updateState() }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()

            if (status != Status.RUNNING
                || context == BuildActivity.BuildContext.PENDING
                || subActivities.isNotEmpty()
            ) return@safeListener

            when (action) {
                BuildActivity.BuildAction.BREAKING, BuildActivity.BuildAction.BREAK -> {
                    side?.let { side ->
                        checkBreak(side)
                    }
                }
                BuildActivity.BuildAction.WRONG_POS_BREAK -> {
                    if (autoPathing) {
                        addSubActivities(BreakGoal(blockPos))
                    }
                }
                else -> {
                    // ToDo: break nearby blocks
                    failedWith(NoExposedSideFound())
                }
            }
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketBlockChange
                || it.packet.blockPosition != blockPos
                || it.packet.blockState.block != Blocks.AIR
            ) return@safeListener

            if (!collectDrops || !autoPathing) {
                ActivityManagerHud.totalBlocksBroken++
                success()
                return@safeListener
            }

            renderActivity.color = ColorHolder(252, 3, 207)

            if (drop.block == Blocks.AIR) return@safeListener

            addSubActivities(
                PickUpDrops(drop, minAmount = minCollectAmount)
            )
        }
    }

    override fun SafeClientEvent.onInitialize() {
        updateState()

        side?.let {
            checkBreak(it)
        }
    }

    private fun SafeClientEvent.updateState() {
        val currentState = world.getBlockState(blockPos)

        if (world.isAirBlock(blockPos) || currentState.block in BuildTools.ignoredBlocks) {
            ActivityManagerHud.totalBlocksBroken++
            success()
            return
        }

        getMiningSide(blockPos, BuildTools.maxReach)?.let {
            action = BuildActivity.BuildAction.BREAK
            hitVec = getHitVec(blockPos, it)
            side = it
            rotation = getRotationTo(getHitVec(blockPos, it))
        } ?: run {
            getMiningSide(blockPos)?.let {
                action = BuildActivity.BuildAction.WRONG_POS_BREAK
                hitVec = getHitVec(blockPos, it)
            } ?: run {
                action = BuildActivity.BuildAction.INVALID_BREAK
                hitVec = Vec3d.ZERO
            }
            playerController.resetBlockRemoving()
            side = null
            rotation = null
        }

        renderActivity.color = action.color
    }

    private fun SafeClientEvent.checkBreak(side: EnumFacing) {
        val currentState = world.getBlockState(blockPos)

        initState = currentState
        drop = currentState.block.getItemDropped(currentState, Random(), 0)

        if (!player.capabilities.isCreativeMode
//            && currentState.block.isToolEffective("pickaxe", currentState)
            && player.getHeldItem(EnumHand.MAIN_HAND).item != Items.DIAMOND_PICKAXE
        ) { // ToDo: get optimal tool
            context = BuildActivity.BuildContext.RESTOCK

            addSubActivities(AcquireItemInActiveHand(Items.DIAMOND_PICKAXE))
            return
        }

        ticksNeeded = ceil((1 / currentState
            .getPlayerRelativeBlockHardness(player, world, blockPos)) * BuildTools.miningSpeedFactor).toInt()
        timeout = ticksNeeded * 50L + 2000L

        var needToHandleLiquid = false

        EnumFacing.values()
            .filter { it != EnumFacing.UP }
            .map { blockPos.offset(it) }
            .filter { world.getBlockState(it).isLiquid }
            .forEach {
                val placeActivity = PlaceBlock(it, BuildTools.defaultFillerMat.defaultState)
                placeActivity.context = BuildActivity.BuildContext.LIQUID

                addSubActivities(placeActivity)
                needToHandleLiquid = true
            }

        if (needToHandleLiquid) return

        doBreak(side)
    }

    private fun SafeClientEvent.doBreak(side: EnumFacing) {
        val isCreative = player.capabilities.isCreativeMode

        val successDamage = if (player.capabilities.isCreativeMode) {
            connection.sendPacket(CPacketPlayerDigging(
                CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, side
            ))
            playerController.onPlayerDestroyBlock(blockPos)
        } else {
            action = BuildActivity.BuildAction.BREAKING
            renderActivity.color = action.color
            playerController.onPlayerDamageBlock(blockPos, side)
        }

        if (!successDamage) {
            failedWith(BlockBreakingException())
            return
        }

        mc.effectRenderer.addBlockHitEffects(blockPos, side)
        player.swingArm(EnumHand.MAIN_HAND)

        if (BuildTools.breakDelay != 0) addSubActivities(Wait(BuildTools.breakDelay * 50L - 5L))

        if (ticksNeeded == 1 || isCreative) context = BuildActivity.BuildContext.PENDING
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PickUpDrops -> {
                ActivityManagerHud.totalBlocksBroken++
                success()
            }
//            is Wait -> setBuildBlockOnPending()
            is AcquireItemInActiveHand, is PlaceBlock -> {
                status = Status.UNINITIALIZED
            }
        }
    }

    override fun SafeClientEvent.onFailure(exception: Exception): Boolean {
        playerController.resetBlockRemoving()
        return false
    }

    class NoExposedSideFound : Exception("No exposed side found")
    class BlockBreakingException : Exception("Block breaking failed")
}