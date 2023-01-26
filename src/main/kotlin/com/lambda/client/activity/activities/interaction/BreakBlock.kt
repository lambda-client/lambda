package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.highlevel.BuildBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.travel.BreakGoal
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RenderAABBActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.block
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import com.lambda.client.util.world.isLiquid
import net.minecraft.block.material.Material
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.ceil

class BreakBlock(
    private val blockPos: BlockPos,
    private val doPending: Boolean = false,
    private val collectDrops: Boolean = false,
    private val miningSpeedFactor: Float = 1.0f,
    private val minCollectAmount: Int = 1,
    override var timeout: Long = 200L,
    override val maxAttempts: Int = 8,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override var rotation: Vec2f = Vec2f.ZERO
) : TimeoutActivity, AttemptActivity, RotatingActivity, RenderAABBActivity, Activity() {
    private var ticksNeeded = 0
    private var initState = Blocks.AIR.defaultState
    private var drop: Item = Items.AIR

    private val renderActivity = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos,
        ColorHolder(222, 0, 0)
    ).also { toRender.add(it) }

    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(blockPos)

        if (currentState.material == Material.AIR) {
            success()
            return
        }

        if (player.getHeldItem(EnumHand.MAIN_HAND).item != Items.DIAMOND_PICKAXE) { // ToDo: get optimal tool
            val owner = owner

            if (owner is BuildBlock) {
                owner.context = BuildBlock.Context.RESTOCK
            }

            addSubActivities(AcquireItemInActiveHand(Items.DIAMOND_PICKAXE))
            return
        }

        var needToHandleLiquid = false

        EnumFacing.values().forEach {
            if (it == EnumFacing.DOWN) return@forEach

            val neighbour = blockPos.offset(it)
            if (world.getBlockState(neighbour).isLiquid) {
                val owner = owner

                if (owner is BuildBlock) {
                    owner.context = BuildBlock.Context.LIQUID
                }

                addSubActivities(PlaceBlock(neighbour, BuildTools.defaultFillerMat.defaultState))
                needToHandleLiquid = true
            }
        }

        if (needToHandleLiquid) return

        initState = currentState
        drop = currentState.block.getItemDropped(currentState, Random(), 0)

        renderActivity.color = ColorHolder(240, 222, 60)

        ticksNeeded = ceil((1 / currentState.getPlayerRelativeBlockHardness(player, world, blockPos)) * miningSpeedFactor).toInt()
        timeout = ticksNeeded * 50L + 2000L

        if (!(ticksNeeded == 1 || player.capabilities.isCreativeMode)) return

        getMiningSide(blockPos, BuildTools.maxReach)?.let { side ->
            rotation = getRotationTo(getHitVec(blockPos, side))

            playerController.onPlayerDamageBlock(blockPos, side)
            mc.effectRenderer.addBlockHitEffects(blockPos, side)
            player.swingArm(EnumHand.MAIN_HAND)

            if (BuildTools.breakDelay == 0) {
                setBuildBlockOnPending()
            } else {
                addSubActivities(Wait(BuildTools.placeDelay * 50L - 5L))
            }
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener
            if (owner.status == Status.PENDING) return@safeListener

            getMiningSide(blockPos, BuildTools.maxReach)?.let { side ->
                rotation = getRotationTo(getHitVec(blockPos, side))

                playerController.onPlayerDamageBlock(blockPos, side)
                mc.effectRenderer.addBlockHitEffects(blockPos, side)
                player.swingArm(EnumHand.MAIN_HAND)

                if (doPending && (ticksNeeded == 1 || player.capabilities.isCreativeMode)) {
                    if (BuildTools.breakDelay == 0) {
                        owner.status = Status.PENDING
                    } else {
                        addSubActivities(Wait(BuildTools.placeDelay * 50L - 5L))
                    }
                }
            } ?: run {
                getMiningSide(blockPos)?.let {
                    if (autoPathing && subActivities.filterIsInstance<BreakGoal>().isEmpty()) {
                        addSubActivities(BreakGoal(blockPos))
                    }
                } ?: run {
                    failedWith(NoExposedSideFound())
                }
            }
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet is SPacketBlockChange
                && it.packet.blockPosition == blockPos
                && it.packet.blockState.block == Blocks.AIR
            ) {
                if (!collectDrops || !autoPathing) {
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
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PickUpDrops -> {
                success()
            }
            is Wait -> setBuildBlockOnPending()
            is AcquireItemInActiveHand, is PlaceBlock -> {
                status = Status.UNINITIALIZED
            }
        }
    }

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childException !is BreakGoal.NoPathToBreakFound) return false

        if (owner !is BuildBlock) return false

        owner.status = Status.UNINITIALIZED
        return true
    }

    override fun SafeClientEvent.onFailure(exception: Exception): Boolean {
        playerController.resetBlockRemoving()
        return false
    }

    private fun setBuildBlockOnPending() {
        if (!doPending || owner !is BuildBlock) return

        owner.status = Status.PENDING
    }

    class NoExposedSideFound : Exception("No exposed side found")
}