package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.*
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.placeBlock
import com.lambda.mixin.entity.MixinEntity
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.concurrent.ConcurrentHashMap

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
    private val page by setting("Page", Page.GENERAL)

    private val blockSelectionMode by setting("Block Selection Mode", ScaffoldBlockSelectionMode.ANY)
    private val tower by setting("Tower", true, { page == Page.GENERAL })
    private val spoofHotbar by setting("Spoof Hotbar", true, { page == Page.GENERAL })
    val safeWalk by setting("Safe Walk", true, { page == Page.GENERAL })
    private val sneak by setting("Sneak", true, { page == Page.GENERAL })
    private val visibleSideCheck by setting("Visible side check", true, { page == Page.GENERAL })
    private val delay by setting("Delay", 0, 0..10, 1, { page == Page.GENERAL }, unit = " ticks")
    private val timeout by setting("Timeout", 50, 1..40, 1, { page == Page.GENERAL }, unit = " ticks")
    private val attempts by setting("Placement Search Depth", 3, 0..7, 1, { page == Page.GENERAL })
    private val maxPending by setting("Max Pending", 2, 0..5, 1, { page == Page.GENERAL })
    private val below by setting("Max Tower Distance", 0.3, 0.0..2.0, 0.01, { page == Page.GENERAL })
    private val filled by setting("Filled", true, { page == Page.RENDER }, description = "Renders surfaces")
    private val outline by setting("Outline", true, { page == Page.RENDER }, description = "Renders outline")
    private val alphaFilled by setting("Alpha Filled", 26, 0..255, 1, { filled && page == Page.RENDER }, description = "Alpha for surfaces")
    private val alphaOutline by setting("Alpha Outline", 26, 0..255, 1, { outline && page == Page.RENDER }, description = "Alpha for outline")
    private val thickness by setting("Outline Thickness", 2f, .25f..4f, .25f, { outline && page == Page.RENDER }, description = "Changes thickness of the outline")
    private val pendingBlockColor by setting("Pending Color", ColorHolder(0, 0, 255))

    val blockSelectionWhitelist = setting(CollectionSetting("BlockWhitelist", linkedSetOf("minecraft:obsidian"), { false }))
    val blockSelectionBlacklist = setting(CollectionSetting("BlockBlacklist", linkedSetOf("minecraft:white_shulker_box", "minecraft:orange_shulker_box",
        "minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box", "minecraft:lime_shulker_box",
        "minecraft:pink_shulker_box", "minecraft:gray_shulker_box", "minecraft:silver_shulker_box", "minecraft:cyan_shulker_box",
        "minecraft:purple_shulker_box", "minecraft:blue_shulker_box", "minecraft:brown_shulker_box", "minecraft:green_shulker_box",
        "minecraft:red_shulker_box", "minecraft:black_shulker_box", "minecraft:crafting_table", "minecraft:dropper",
        "minecraft:hopper", "minecraft:dispenser", "minecraft:ender_chest", "minecraft:furnace"),
        { false }))

    private enum class Page {
        GENERAL, RENDER
    }

    private enum class ScaffoldBlockSelectionMode(
        override val displayName: String,
        val filter: (Item) -> Boolean): DisplayEnum {
        ANY("Any", { it is ItemBlock }),
        WHITELIST("Whitelist", { it is ItemBlock && blockSelectionWhitelist.contains(it.registryName.toString()) }),
        BLACKLIST("Blacklist", { it is ItemBlock && !blockSelectionBlacklist.contains(it.registryName.toString()) })
    }

    private var placeInfo: PlaceInfo? = null
    private val renderer = ESPRenderer()

    private val placeTimer = TickTimer(TimeUnit.TICKS)
    private var towerTimer: TickTimer = TickTimer(TimeUnit.TICKS)

    private val pendingBlocks = ConcurrentHashMap<BlockPos, PendingBlock>()

    init {
        onEnable {
            towerTimer.reset()
        }

        onDisable {
            placeInfo = null
            pendingBlocks.clear()
        }

        safeListener<PacketEvent.Receive> { event ->
            when (val packet = event.packet) {
                is SPacketPlayerPosLook -> {
                    pendingBlocks.forEach {
                        world.setBlockState(it.key, it.value.blockState)
                    }
                    pendingBlocks.clear()
                }
                is SPacketBlockChange -> {
                    pendingBlocks[packet.blockPosition]?.let { pendingBlock ->
                        if (pendingBlock.block == packet.blockState.block) {
                            pendingBlocks.remove(packet.blockPosition)
                        } else {
                            // probably ItemStack emtpy
                            if (packet.blockState.block == Blocks.AIR) {
                                pendingBlocks.forEach {
                                    world.setBlockState(it.key, it.value.blockState)
                                }
                                pendingBlocks.clear()
                            }
                            LambdaMod.LOG.warn("[$chatName] Other confirm: ${packet.blockPosition} ${packet.blockState.block}")
                        }
                    }
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            if (!tower || !mc.gameSettings.keyBindJump.isKeyDown || !isHoldingBlock) return@safeListener
            if (shouldTower) {
                player.jump()
                if (towerTimer.tick(30)) {
                    // reset pos back onto top block
                    player.motionY = -0.3
                }
            }
        }

        listener<RenderWorldEvent> {
            renderer.aFilled = if (filled) alphaFilled else 0
            renderer.aOutline = if (outline) alphaOutline else 0
            renderer.thickness = thickness

            pendingBlocks.keys.forEach {
                renderer.add(it, pendingBlockColor)
            }

            renderer.render(clear = true)
        }

        safeListener<PushOutOfBlocksEvent> {
            if (tower) {
                it.cancel()
            }
        }
    }

    private val SafeClientEvent.isHoldingBlock: Boolean
        get() = player.serverSideItem.item is ItemBlock

    private val SafeClientEvent.shouldTower: Boolean
        get() = !player.onGround
            && world.getCollisionBoxes(player, player.entityBoundingBox.offset(0.0, -below, 0.0)).isNotEmpty()
            && mc.player.speed < 0.1
            && getHeldScaffoldBlock() != null

    init {
        safeListener<ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            pendingBlocks.values
                .filter { it.age > timeout * 50L }
                .forEach { pendingBlock ->
                    LambdaMod.LOG.warn("[$chatName] Timeout: ${pendingBlock.blockPos}")
                    pendingBlocks.remove(pendingBlock.blockPos)
                    world.setBlockState(pendingBlock.blockPos, pendingBlock.blockState)
                }

            placeInfo?.let { placeInfo ->
                pendingBlocks[placeInfo.placedPos]?.let {
                    if (it.age < timeout * 50L) {
                        return@safeListener
                    }
                }
                swap()?.let { block ->
                    place(placeInfo, block)
                    sendPlayerPacket {
                        rotate(getRotationTo(placeInfo.hitVec))
                    }
                }
            }
        }

        safeListener<OnUpdateWalkingPlayerEvent> { event ->
            if (event.phase != Phase.PRE) return@safeListener

            placeInfo = getNeighbour(player.flooredPosition.down(), attempts, visibleSideCheck = visibleSideCheck)
        }
    }

    private fun SafeClientEvent.swap(): Block? {
        getHeldScaffoldBlock()?.let { return it }
        getBlockSlot()?.let { slot ->
            if (spoofHotbar) spoofHotbar(slot)
            else swapToSlot(slot)
            return slot.stack.item.block
        }
        return null
    }

    private fun SafeClientEvent.place(placeInfo: PlaceInfo, blockToPlace: Block) {
        if (placeTimer.tick(delay.toLong())
            && pendingBlocks.size < maxPending
        ) {
            val shouldSneak = sneak && !player.isSneaking
            if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))

            placeBlock(placeInfo)

            pendingBlocks[placeInfo.placedPos] = PendingBlock(placeInfo.placedPos, world.getBlockState(placeInfo.placedPos), blockToPlace)
            world.setBlockState(placeInfo.placedPos, Blocks.BARRIER.defaultState)

            if (shouldSneak) connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
        }
    }

    private fun SafeClientEvent.getHeldScaffoldBlock(): Block? {
        playerController.syncCurrentPlayItem()
        if (blockSelectionMode.filter(player.heldItemMainhand.item)) {
            return player.heldItemMainhand.item.block
        }
        if (blockSelectionMode.filter(player.heldItemOffhand.item)) {
            return player.heldItemOffhand.item.block
        }
        return null
    }

    private fun SafeClientEvent.getBlockSlot(): HotbarSlot? {
        playerController.syncCurrentPlayItem()
        return player.hotbarSlots.firstItem<ItemBlock, HotbarSlot> { blockSelectionMode.filter(it.item) }
    }

    private data class PendingBlock(
        val blockPos: BlockPos,
        val blockState: IBlockState,
        val block: Block,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val age get() = System.currentTimeMillis() - timestamp
    }
}