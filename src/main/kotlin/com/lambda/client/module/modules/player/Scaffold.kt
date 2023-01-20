package com.lambda.client.module.modules.player

import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.OnUpdateWalkingPlayerEvent
import com.lambda.client.event.events.PushOutOfBlocksEvent
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.prevPosVector
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.HotbarSlot
import com.lambda.client.util.items.firstItem
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.swapToSlot
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.placeBlock
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import kotlin.math.roundToInt

object Scaffold : Module(
    name = "Scaffold",
    description = "Places blocks under you",
    category = Category.PLAYER,
    modulePriority = 500
) {
    private val tower by setting("Tower", true)
    private val spoofHotbar by setting("Spoof Hotbar", true)
    val safeWalk by setting("Safe Walk", true)
    private val sneak by setting("Sneak", true)
    private val strictDirection by setting("Strict Direction", true)
    private val delay by setting("Delay", 2, 1..10, 1, unit = " ticks")
    private val maxRange by setting("Max Range", 1, 0..3, 1)
    private val noGhost by setting("No Ghost Blocks", false)

    private val placeTimer = TickTimer(TimeUnit.TICKS)

    private var lastHitVec: Vec3d? = null
    private var placeInfo: PlaceInfo? = null
    private var inactiveTicks = 69

    override fun isActive(): Boolean {
        return isEnabled && inactiveTicks <= 5
    }

    init {
        onDisable {
            placeInfo = null
            inactiveTicks = 69
        }

        safeListener<OnUpdateWalkingPlayerEvent> { event ->
            if (event.phase == Phase.PRE) {
                inactiveTicks++

                placeInfo = calcNextPos()?.let {
                    getNeighbour(it, 1, visibleSideCheck = strictDirection, sides = arrayOf(EnumFacing.DOWN))
                        ?: getNeighbour(it, 3, visibleSideCheck = strictDirection, sides = EnumFacing.HORIZONTALS)
                }
                placeInfo?.let {
                    lastHitVec = it.hitVec
                    val rotation = getRotationTo(it.hitVec)
                    event.rotation = rotation
                }
            } else if (event.phase == Phase.POST) {
                placeInfo?.let { pi ->
                    if (swap()) {
                        if (tower && mc.player.movementInput.jump) {
                            val shouldSneak = sneak && !player.isSneaking
                            if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
                            placeBlock(pi, noGhost = false) // noGhost true usually causes problems and has no real benefit here
                            if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
                            mc.player.jump()
                        } else {
                            if (placeTimer.tick(delay, true)) {
                                val shouldSneak = sneak && !player.isSneaking
                                if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
                                placeBlock(pi, noGhost = noGhost)
                                if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
                            }
                        }
                    }
                }
                if (inactiveTicks > 5) {
                    resetHotbar()
                }
            }
        }

        safeListener<PushOutOfBlocksEvent> {
            if (tower) {
                it.cancel()
            }
        }
    }

    private fun SafeClientEvent.calcNextPos(): BlockPos? {
        val posVec = player.positionVector
        val blockPos = posVec.toBlockPos()
        return checkPos(blockPos)
            ?: run {
                val realMotion = posVec.subtract(player.prevPosVector)
                val nextPos = blockPos.add(roundToRange(realMotion.x), 0, roundToRange(realMotion.z))
                checkPos(nextPos)
            }
    }

    private fun SafeClientEvent.checkPos(blockPos: BlockPos): BlockPos? {
        val center = Vec3d(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5)
        val rayTraceResult = world.rayTraceBlocks(
            center,
            center.subtract(0.0, 0.5, 0.0),
            false,
            true,
            false
        )
        return blockPos.down().takeIf { rayTraceResult?.typeOfHit != RayTraceResult.Type.BLOCK }
    }

    private fun roundToRange(value: Double) =
        (value * 2.5 * maxRange).roundToInt().coerceAtMost(maxRange)

    private fun SafeClientEvent.swap(): Boolean {
        if (player.heldItemMainhand.item is ItemBlock || player.heldItemOffhand.item is ItemBlock) {
            inactiveTicks = 0;
            return true;
        }
        getBlockSlot()?.let { slot ->
            if (spoofHotbar) spoofHotbar(slot)
            else swapToSlot(slot)
            inactiveTicks = 0
            return true
        }
        return false
    }

    private fun SafeClientEvent.getBlockSlot(): HotbarSlot? {
        playerController.syncCurrentPlayItem()
        return player.hotbarSlots.firstItem<ItemBlock, HotbarSlot>()
    }
}