package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.OnUpdateWalkingPlayerEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.placeBlock
import com.lambda.mixin.entity.MixinEntity
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * @see MixinEntity.moveInvokeIsSneakingPre
 * @see MixinEntity.moveInvokeIsSneakingPost
 */
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
    private val strictDirection by setting("Strict Direction", false)
    private val delay by setting("Delay", 2, 0..10, 1, unit = " ticks")
    private val timeout by setting("Timeout", 20, 1..40, 1, unit = " ticks")
    private val maxRange by setting("Max Range", 1, 0..3, 1)
    private val maxPending by setting("Max Pending", 1, 0..10, 1)
    private val below by setting("below", 1.0, 0.0..5.0, 0.1)
    private val filled by setting("Filled", true, description = "Renders surfaces")
    private val outline by setting("Outline", true, description = "Renders outline")
    private val alphaFilled by setting("Alpha Filled", 26, 0..255, 1, { filled }, description = "Alpha for surfaces")
    private val alphaOutline by setting("Alpha Outline", 26, 0..255, 1, { outline }, description = "Alpha for outline")
    private val thickness by setting("Outline Thickness", 2f, .25f..4f, .25f, { outline }, description = "Changes thickness of the outline")

    private var placeInfo: PlaceInfo? = null
    private val renderer = ESPRenderer()

    private val placeTimer = TickTimer(TimeUnit.TICKS)
    private val rubberBandTimer = TickTimer(TimeUnit.TICKS)
    private var lastPosVec = Vec3d.ZERO

    private val pendingBlocks = ConcurrentHashMap<BlockPos, PendingBlock>()

    override fun isActive(): Boolean {
        return isEnabled
    }

    init {
        onDisable {
            placeInfo = null
            pendingBlocks.clear()
        }

        listener<PacketEvent.Receive> { event ->
            when (val packet = event.packet) {
                is SPacketPlayerPosLook -> {
                    rubberBandTimer.reset()
                    MessageSendHelper.sendChatMessage("RUBBER")
                    runSafe {
                        pendingBlocks.values.forEach {
                            world.setBlockState(it.blockPos, Blocks.AIR.defaultState)
                        }
                    }
                }

                is SPacketBlockChange -> {
                    pendingBlocks[packet.blockPosition]?.let { pendingBlock ->
                        if (pendingBlock.block == packet.blockState.block) {
                            pendingBlocks.remove(packet.blockPosition)
                            LambdaMod.LOG.error("Confirmed: $pendingBlock")
                        } else {
                            LambdaMod.LOG.error("Other confirm: ${packet.blockPosition} ${packet.blockState.block}")
                        }
                    }
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            if (!tower || !mc.gameSettings.keyBindJump.isKeyDown || !isHoldingBlock) return@safeListener
            if (rubberBandTimer.tick(10, false)) {
                if (shouldTower) {
                    if (floor(lastPosVec.y) < floor(player.posY)) {
                        connection.sendPacket(CPacketPlayer(true))
                    }

                    player.motionY = 0.41999998688697815

                    lastPosVec = player.positionVector
                }
            } else if (player.fallDistance <= 2.0f) {
                player.motionY = -0.169
            }
        }

        safeListener<RenderWorldEvent> {
            renderer.aFilled = if (filled) alphaFilled else 0
            renderer.aOutline = if (outline) alphaOutline else 0
            renderer.thickness = thickness

            pendingBlocks.keys.forEach {
                renderer.add(it, ColorHolder(0, 0, 255))
            }

            renderer.render(clear = true)
        }
    }

    private val SafeClientEvent.isHoldingBlock: Boolean
        get() = player.serverSideItem.item is ItemBlock

    private val SafeClientEvent.shouldTower: Boolean
        get() = !player.onGround
//            && pendingBlocks.isEmpty()
            && world.getCollisionBoxes(player, player.entityBoundingBox.offset(0.0, -below, 0.0)).isNotEmpty()

    init {
        safeListener<ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            pendingBlocks.values
                .filter { it.age > timeout * 50L }
                .forEach { pendingBlock ->
                    LambdaMod.LOG.error("Timeout: ${pendingBlock.blockPos}")
                    pendingBlocks.remove(pendingBlock.blockPos)
                }

            placeInfo?.let { placeInfo ->
                pendingBlocks[placeInfo.placedPos]?.let {
                    if (it.age < timeout * 50L) {
                        LambdaMod.LOG.error("Age: ${it.age}")
                        return@safeListener
                    }
                }

                swapAndPlace(placeInfo)
                sendPlayerPacket {
                    rotate(getRotationTo(placeInfo.hitVec))
                }
            }
        }

        safeListener<OnUpdateWalkingPlayerEvent> { event ->
            if (event.phase != Phase.PRE) return@safeListener

            placeInfo = getNeighbour(player.flooredPosition.down(), maxRange, visibleSideCheck = strictDirection)
        }
    }

    private fun SafeClientEvent.swapAndPlace(placeInfo: PlaceInfo) {
        getBlockSlot()?.let { slot ->
            if (spoofHotbar) spoofHotbar(slot)
            else swapToSlot(slot)

            if (placeTimer.tick(delay.toLong())
                && pendingBlocks.size < maxPending
            ) {
                val shouldSneak = sneak && !player.isSneaking
                if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))

                placeBlock(placeInfo)
                world.setBlockState(placeInfo.placedPos, Blocks.BARRIER.defaultState)
                pendingBlocks[placeInfo.placedPos] = PendingBlock(placeInfo.placedPos, slot.stack.item.block)
                LambdaMod.LOG.error("Placed: ${placeInfo.placedPos} ${slot.stack.item.block.localizedName}")

                if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        }
    }

    private fun SafeClientEvent.getBlockSlot(): HotbarSlot? {
        playerController.syncCurrentPlayItem()
        return if (isUsableBlock(player.heldItemMainhand.item.block)) {
            player.hotbarSlots[player.inventory.currentItem]
        } else {
            player.hotbarSlots.firstOrNull { isUsableBlock(it.stack.item.block) }
        }
    }

    private data class PendingBlock(val timestamp: Long, val blockPos: BlockPos, val block: Block) {
        constructor(blockPos: BlockPos, block: Block) : this(System.currentTimeMillis(), blockPos, block)

        val age get() = System.currentTimeMillis() - timestamp
    }

    private fun isUsableBlock(block: Block) = InventoryManager.ejectList.contains(block.item.registryName.toString())
}