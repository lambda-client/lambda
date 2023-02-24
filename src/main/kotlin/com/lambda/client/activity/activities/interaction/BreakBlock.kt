package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.travel.BreakGoal
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.*
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
import java.util.*
import kotlin.math.ceil
import kotlin.properties.Delegates

class BreakBlock(
    private val blockPos: BlockPos,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1,
    override var timeout: Long = 200L,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override var rotation: Vec2f? = null,
    override var hitVec: Vec3d = Vec3d.ZERO
) : TimeoutActivity, AttemptActivity, RotatingActivity, RenderAABBActivity, BuildActivity, TimedActivity, Activity() {
    private var side: EnumFacing? = null
    private var ticksNeeded = 0
    private var initState = Blocks.AIR.defaultState
    private var drop: Item = Items.AIR

    override var context: BuildActivity.BuildContext by Delegates.observable(BuildActivity.BuildContext.NONE) { _, _, new ->
        renderContext.color = new.color
        if (owner.subActivities.remove(this)) owner.subActivities.add(this)
    }

    override var action: BuildActivity.BuildAction by Delegates.observable(BuildActivity.BuildAction.UNINIT) { _, _, new ->
        renderAction.color = new.color
        if (owner.subActivities.remove(this)) owner.subActivities.add(this)
    }

    override var earliestFinish: Long
        get() = BuildTools.breakDelay.toLong()
        set(_) {}

    private val renderContext = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, context.color
    ).also { toRender.add(it) }

    private val renderAction = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, action.color
    ).also { toRender.add(it) }

    init {
        runSafe { updateState() }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()

            if (action != BuildActivity.BuildAction.BREAKING) return@safeListener

            side?.let { side ->
                checkBreak(side)
            }
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketBlockChange
                || it.packet.blockPosition != blockPos
                || it.packet.blockState.block != Blocks.AIR
            ) return@safeListener

            finish()
        }
    }

    override fun SafeClientEvent.onInitialize() {
        updateState()

        when (action) {
            BuildActivity.BuildAction.BREAKING, BuildActivity.BuildAction.BREAK -> {
                side?.let { side ->
                    checkBreak(side)
                }
            }
            BuildActivity.BuildAction.WRONG_POS_BREAK -> {
                if (autoPathing) addSubActivities(BreakGoal(blockPos))
            }
            else -> {
                // ToDo: break nearby blocks
//                    failedWith(NoExposedSideFound())
            }
        }
    }

    override fun SafeClientEvent.onCancel() {
        playerController.resetBlockRemoving()
    }

    private fun SafeClientEvent.finish() {
        if (!collectDrops || !autoPathing) {
            ActivityManagerHud.totalBlocksBroken++
            success()
            return
        }

        renderAction.color = ColorHolder(252, 3, 207)

        if (drop.block == Blocks.AIR) return

        addSubActivities(
            PickUpDrops(drop, minAmount = minCollectAmount)
        )
    }

    private fun SafeClientEvent.updateState() {
        getMiningSide(blockPos, BuildTools.maxReach)?.let {
            if (action != BuildActivity.BuildAction.BREAKING) {
                action = BuildActivity.BuildAction.BREAK
            }
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

        val successDamage = if (ticksNeeded == 1 || isCreative) {
            context = BuildActivity.BuildContext.PENDING
            connection.sendPacket(CPacketPlayerDigging(
                CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, side
            ))
            playerController.onPlayerDestroyBlock(blockPos)
        } else {
            action = BuildActivity.BuildAction.BREAKING
            renderAction.color = action.color
            playerController.onPlayerDamageBlock(blockPos, side)
        }

//        if (!successDamage) {
//            failedWith(BlockBreakingException())
//            return
//        }

        player.swingArm(EnumHand.MAIN_HAND)
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PickUpDrops -> {
                ActivityManagerHud.totalBlocksBroken++
                success()
            }
            is AcquireItemInActiveHand, is PlaceBlock -> {
                status = Status.UNINITIALIZED
                context = BuildActivity.BuildContext.NONE
                updateState()
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