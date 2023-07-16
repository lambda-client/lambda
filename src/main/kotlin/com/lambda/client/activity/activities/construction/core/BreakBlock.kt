package com.lambda.client.activity.activities.construction.core

import baritone.api.BaritoneAPI
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.core.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.types.*
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.mixin.extension.blockHitDelay
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import com.lambda.client.util.world.isLiquid
import kotlinx.coroutines.launch
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.ceil
import kotlin.properties.Delegates

class BreakBlock(
    val blockPos: BlockPos,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1,
    private val forceSilk: Boolean = false,
    private val forceNoSilk: Boolean = false,
    private val forceFortune: Boolean = false,
    private val forceNoFortune: Boolean = false,
    override var timeout: Long = Long.MAX_VALUE, // ToDo: Reset timeouted breaks blockstates
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0,
    override val aabbCompounds: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override val overlayTexts: MutableSet<RenderOverlayTextActivity.Companion.RenderOverlayText> = mutableSetOf(),
    override var rotation: Vec2f? = null,
    override var distance: Double = 1337.0,
) : BuildActivity, TimeoutActivity, AttemptActivity, RotatingActivity, TimedActivity, RenderAABBActivity, RenderOverlayTextActivity, Activity() {
    private var side: EnumFacing? = null
    private var ticksNeeded = 0
    private var drops: Item = Items.AIR

    override var context: BuildActivity.Context by Delegates.observable(BuildActivity.Context.NONE) { _, old, new ->
        if (old == new) return@observable
        renderContextAABB.color = new.color

        renderContextOverlay.text = new.name
        renderContextOverlay.color = new.color
    }

    override var availability: BuildActivity.Availability by Delegates.observable(BuildActivity.Availability.NONE) { _, old, new ->
        if (old == new) return@observable
        renderAvailabilityAABB.color = new.color

        renderAvailabilityOverlay.text = new.name
        renderAvailabilityOverlay.color = new.color
    }

    override var type: BuildActivity.Type by Delegates.observable(BuildActivity.Type.BREAK_BLOCK) { _, old, new ->
        if (old == new) return@observable
        renderTypeAABB.color = new.color

        renderTypeOverlay.text = new.name
        renderTypeOverlay.color = new.color
    }

    private val renderContextAABB = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, context.color
    ).also { aabbCompounds.add(it) }

    private val renderContextOverlay = RenderOverlayTextActivity.Companion.RenderOverlayText(
        context.name, context.color, blockPos.toVec3dCenter(), 0
    ).also { overlayTexts.add(it) }

    private val renderAvailabilityAABB = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, availability.color
    ).also { aabbCompounds.add(it) }

    private val renderAvailabilityOverlay = RenderOverlayTextActivity.Companion.RenderOverlayText(
        availability.name, availability.color, blockPos.toVec3dCenter(), 1
    ).also { overlayTexts.add(it) }

    private val renderTypeAABB = RenderAABBActivity.Companion.RenderBlockPos(
        blockPos, type.color
    ).also { aabbCompounds.add(it) }

    private val renderTypeOverlay = RenderOverlayTextActivity.Companion.RenderOverlayText(
        type.name, type.color, blockPos.toVec3dCenter(), 2
    ).also { overlayTexts.add(it) }

    override var earliestFinish: Long
        get() = BuildTools.breakDelay.toLong()
        set(_) {}

    init {
        runSafe {
            if (!world.worldBorder.contains(blockPos) || world.isOutsideBuildHeight(blockPos)) {
                // ToDo: add support for placing blocks outside of world border
                failedWith(BlockOutsideOfBoundsException(blockPos))
                return@runSafe
            }

            updateState() // ToDo: check if needed
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()

            if (world.isAirBlock(blockPos)) {
                finish()
                return@safeListener
            }

            if (subActivities.isNotEmpty()
                || status == Status.UNINITIALIZED
            ) return@safeListener

            resolveAvailability()
        }

        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketBlockChange
                || it.packet.blockPosition != blockPos
                || it.packet.blockState.block != Blocks.AIR
            ) return@safeListener

            defaultScope.launch {
                onMainThreadSafe {
                    finish()
                }
            }
        }
    }

    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(blockPos)

        if (currentState.block in BuildTools.ignoredBlocks
            || currentState.getBlockHardness(world, blockPos) < 0
        ) {
            success()
            return
        }

        updateState()
        resolveAvailability()
    }

    override fun SafeClientEvent.onCancel() {
        if (context == BuildActivity.Context.IN_PROGRESS) playerController.resetBlockRemoving()
    }

    private fun SafeClientEvent.finish() {
//        if (!collectDrops || !autoPathing || drops == Items.AIR) {
        if (!collectDrops || drops == Items.AIR) {
            ActivityManagerHud.totalBlocksBroken++
            success()
            return
        }

        if (context == BuildActivity.Context.PICKUP) return

        context = BuildActivity.Context.PICKUP

        addSubActivities(
            PickUpDrops(drops, minAmount = minCollectAmount)
        )
    }

    private fun SafeClientEvent.updateState() {
        if (checkLiquids()) return

        updateProperties()

        getMiningSide(blockPos, BuildTools.maxReach)?.let {
            val hitVec = getHitVec(blockPos, it)

            /* prevent breaking the block the player is standing on */
            if (player.flooredPosition.down() == blockPos
                && !world.getBlockState(blockPos.down()).isSideSolid(world, blockPos.down(), EnumFacing.UP)
            ) {
                availability = BuildActivity.Availability.BLOCKED_BY_PLAYER
                distance = player.distanceTo(hitVec)
                return
            }
            availability = BuildActivity.Availability.VALID
            distance = player.distanceTo(hitVec)
            side = it
            rotation = getRotationTo(hitVec)
            return
        }

        getMiningSide(blockPos)?.let {
            availability = BuildActivity.Availability.NOT_IN_RANGE
            distance = player.distanceTo(getHitVec(blockPos, it))
        } ?: run {
            availability = BuildActivity.Availability.NOT_EXPOSED
            distance = 1337.0
        }

        playerController.resetBlockRemoving()
        side = null
        rotation = null
    }

    private fun SafeClientEvent.resolveAvailability() {
        when (availability)     {
            BuildActivity.Availability.VALID -> {
                tryBreak()
            }
            BuildActivity.Availability.BLOCKED_BY_PLAYER,
            BuildActivity.Availability.NOT_IN_RANGE -> {
                // Wait for player move
            }
            BuildActivity.Availability.WRONG_ITEM_SELECTED -> {
//                acquireOptimalTool()
            }
            BuildActivity.Availability.NOT_EXPOSED,
            BuildActivity.Availability.NEEDS_LIQUID_HANDLING -> {
                // Wait for other tasks to finish
            }
            else -> {
                // Other cases should not happen
            }
        }
    }

    private fun SafeClientEvent.tryBreak() {
        if (ActivityManager.getCurrentActivity() != this@BreakBlock) return
        if (!hasOptimalTool()) return

        side?.let {
            // if baritone break/place is on while we're breaking it ourselves, we'll get stuck in a loop
            // todo: option to use baritone break/place instead of ours
            BaritoneAPI.getSettings().allowBreak.value = false
            BaritoneAPI.getSettings().allowPlace.value = false
            if (!world.isAirBlock(blockPos) && playerController.onPlayerDamageBlock(blockPos, it)) {
                if ((ticksNeeded == 1 && player.onGround) || player.capabilities.isCreativeMode) {
                    playerController.blockHitDelay = 0
                    context = BuildActivity.Context.PENDING
                } else {
                    context = BuildActivity.Context.IN_PROGRESS
                }

                mc.effectRenderer.addBlockHitEffects(blockPos, it)
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
    }

    // ToDo: 1. currentState.material.isToolNotRequired (if drop is needed it should check if tool has sufficient harvest level)
    // ToDo: 2. ForgeHooks.canHarvestBlock(currentState.block, player, world, blockPos)
    private fun SafeClientEvent.hasOptimalTool(): Boolean {
        if (player.capabilities.isCreativeMode) return true

        val currentState = world.getBlockState(blockPos)

        if (world.isAirBlock(blockPos)) return false

        val currentDestroySpeed = player.heldItemMainhand.getDestroySpeed(currentState)

        player.inventorySlots.maxByOrNull { it.stack.getDestroySpeed(currentState) }?.let {
            if (it.stack.getDestroySpeed(currentState) > currentDestroySpeed
//                && it.stack != player.heldItemMainhand
//                && (!getSilkDrop || EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it.stack) > 0)
            ) {
                context = BuildActivity.Context.RESTOCK

                addSubActivities(SwapOrSwitchToSlot(it))
                return false
            }
        }

        val availableTools = mutableListOf<Item>()

        if (BuildTools.usePickaxe) availableTools.add(Items.DIAMOND_PICKAXE)
        if (BuildTools.useShovel) availableTools.add(Items.DIAMOND_SHOVEL)
        if (BuildTools.useAxe) availableTools.add(Items.DIAMOND_AXE)
        if (BuildTools.useShears) availableTools.add(Items.SHEARS)
        if (BuildTools.useSword) availableTools.add(Items.DIAMOND_SWORD)

        availableTools.maxByOrNull { tool ->
            tool.getDestroySpeed(ItemStack(tool), currentState)
        }?.let { tool ->
            val selectedSpeed = tool.getDestroySpeed(ItemStack(tool), currentState)

            if (selectedSpeed > currentDestroySpeed) {
                context = BuildActivity.Context.RESTOCK

                addSubActivities(AcquireItemInActiveHand(
                    tool,
//                    predicateItem = {
//                        EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 0
//                    }
                ))
                return false
            }

            if (selectedSpeed == 1.0f
                && player.heldItemMainhand.isItemStackDamageable
            ) {
                player.inventorySlots.firstOrNull { !it.stack.isItemStackDamageable }?.let {
                    context = BuildActivity.Context.RESTOCK

                    addSubActivities(SwapOrSwitchToSlot(it))
                    return false
                }
            }
        }

        return true
    }

    private fun SafeClientEvent.checkLiquids(): Boolean {
        var foundLiquid = false

        if (world.getBlockState(blockPos).isLiquid) {
            (parent as? BuildStructure)?.let {
                with(it) {
                    addLiquidFill(blockPos)
                }
            }
            availability = BuildActivity.Availability.NEEDS_LIQUID_HANDLING

            return true
        }

        EnumFacing.entries
            .filter { it != EnumFacing.DOWN }
            .map { blockPos.offset(it) }
            .filter { world.getBlockState(it).isLiquid }
            .forEach { pos ->
                (parent as? BuildStructure)?.let {
                    with(it) {
                        addLiquidFill(pos)
                    }
                }

                foundLiquid = true
            }

        if (foundLiquid) {
            availability = BuildActivity.Availability.NEEDS_LIQUID_HANDLING
        }

        return foundLiquid
    }

    private fun SafeClientEvent.updateProperties() {
        val currentState = world.getBlockState(blockPos)

        if (currentState.block in BuildTools.ignoredBlocks) {
            success()
            return
        }

        ticksNeeded = ceil((1 / currentState
            .getPlayerRelativeBlockHardness(player, world, blockPos)) * BuildTools.miningSpeedFactor).toInt()

        timeout = ticksNeeded * 50L + 2000L

        // ToDo: add silk touch support
        drops = currentState.block.getItemDropped(
            currentState,
            Random(),
            EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, player.heldItemMainhand)
        ) ?: Items.AIR
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        when (childActivity) {
            is PickUpDrops -> {
                ActivityManagerHud.totalBlocksBroken++
                success()
            }
            else -> {
                status = Status.UNINITIALIZED
                updateState()
            }
        }
    }

    override fun SafeClientEvent.onFailure(exception: Exception): Boolean {
        playerController.resetBlockRemoving()
        side = null
        rotation = null
        return false
    }

    class NoExposedSideFound : Exception("No exposed side found")
    class BlockBreakingException : Exception("Block breaking failed")
    class BlockOutsideOfBoundsException(blockPos: BlockPos) : Exception("Block at (${blockPos.asString()}) is outside of world")
    class NoFillerMaterialFoundException: Exception("No filler material in inventory found")
}