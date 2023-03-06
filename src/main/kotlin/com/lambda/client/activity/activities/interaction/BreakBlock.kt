package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.travel.BreakGoal
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.*
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.mixin.extension.blockHitDelay
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.autoPathing
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.*
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getMiningSide
import com.lambda.client.util.world.isLiquid
import kotlinx.coroutines.launch
import net.minecraft.block.state.IBlockState
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
    private val blockPos: BlockPos,
    private val collectDrops: Boolean = false,
    private val minCollectAmount: Int = 1,
    private val forceSilk: Boolean = false,
    private val forceNoSilk: Boolean = false,
    private val forceFortune: Boolean = false,
    private val forceNoFortune: Boolean = false,
    override var timeout: Long = 200L, // ToDo: Reset timeouted breaks blockstates
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0,
    override val toRender: MutableSet<RenderAABBActivity.Companion.RenderAABBCompound> = mutableSetOf(),
    override var rotation: Vec2f? = null,
    override var distance: Double = 1337.0,
) : TimeoutActivity, AttemptActivity, RotatingActivity, RenderAABBActivity, BuildActivity, TimedActivity, Activity() {
    private var side: EnumFacing? = null
    private var ticksNeeded = 0
    private var drops: Item = Items.AIR

    override var context: BuildActivity.BuildContext by Delegates.observable(BuildActivity.BuildContext.NONE) { _, old, new ->
        if (old == new) return@observable
        renderContext.color = new.color
    }

    override var action: BuildActivity.BuildAction by Delegates.observable(BuildActivity.BuildAction.NONE) { _, old, new ->
        if (old == new) return@observable
        renderAction.color = new.color
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
        runSafe {
            if (!world.worldBorder.contains(blockPos) || world.isOutsideBuildHeight(blockPos)) {
                // ToDo: add support for placing blocks outside of world border
                failedWith(BlockOutsideOfBoundsException(blockPos))
                return@runSafe
            }

            val currentState = world.getBlockState(blockPos)

            // ToDo: doesn't work for some reason
            if (currentState.block in BuildTools.ignoredBlocks) {
                success()
                return@runSafe
            }

            updateDrops(currentState)
            updateState()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            updateState()

            if (context != BuildActivity.BuildContext.PENDING
                && world.getBlockState(blockPos) == Blocks.AIR.defaultState
            ) success()

            if (action != BuildActivity.BuildAction.BREAKING
                || subActivities.isNotEmpty()
                || status == Status.UNINITIALIZED
            ) return@safeListener

            side?.let { side ->
                tryBreak(side)
            }
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
        updateState()

        when (action) {
            BuildActivity.BuildAction.BREAKING, BuildActivity.BuildAction.BREAK -> {
                side?.let { side ->
                    tryBreak(side)
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
        if (!collectDrops || !autoPathing || drops == Items.AIR) {
            ActivityManagerHud.totalBlocksBroken++
            success()
            return
        }

        renderAction.color = ColorHolder(252, 3, 207)
        context = BuildActivity.BuildContext.NONE

        addSubActivities(
            PickUpDrops(drops, minAmount = minCollectAmount)
        )
    }

    private fun SafeClientEvent.updateState() {
        getMiningSide(blockPos, BuildTools.maxReach)?.let {
            if (action != BuildActivity.BuildAction.BREAKING) {
                action = BuildActivity.BuildAction.BREAK
            }
            distance = player.distanceTo(getHitVec(blockPos, it))
            side = it
            rotation = getRotationTo(getHitVec(blockPos, it))
        } ?: run {
            getMiningSide(blockPos)?.let {
                action = BuildActivity.BuildAction.WRONG_POS_BREAK
                distance = player.distanceTo(getHitVec(blockPos, it))
            } ?: run {
                action = BuildActivity.BuildAction.INVALID_BREAK
                distance = 1337.0
            }
            playerController.resetBlockRemoving()
            side = null
            rotation = null
        }
    }

    private fun SafeClientEvent.tryBreak(side: EnumFacing) {
        if (checkLiquids() || !hasOptimalTool()) return

        val currentState = world.getBlockState(blockPos)

        // ToDo: add silk touch support
        updateDrops(currentState)

        ticksNeeded = ceil((1 / currentState
            .getPlayerRelativeBlockHardness(player, world, blockPos)) * BuildTools.miningSpeedFactor).toInt()
        timeout = ticksNeeded * 50L + 2000L

        if (!world.isAirBlock(blockPos) && playerController.onPlayerDamageBlock(blockPos, side)) {
            if (ticksNeeded == 1 || player.capabilities.isCreativeMode) {
                playerController.blockHitDelay = 0
                context = BuildActivity.BuildContext.PENDING
            } else {
                action = BuildActivity.BuildAction.BREAKING
            }

            mc.effectRenderer.addBlockHitEffects(blockPos, side)
            player.swingArm(EnumHand.MAIN_HAND)
        }
    }

    // ToDo: 1. currentState.material.isToolNotRequired (if drop is needed it should check if tool has sufficient harvest level)
    // ToDo: 2. ForgeHooks.canHarvestBlock(currentState.block, player, world, blockPos)
    private fun SafeClientEvent.hasOptimalTool(): Boolean {
        if (player.capabilities.isCreativeMode) return true

        val currentState = world.getBlockState(blockPos)
        val currentDestroySpeed = player.heldItemMainhand.getDestroySpeed(currentState)

        player.inventorySlots.maxByOrNull { it.stack.getDestroySpeed(currentState) }?.let {
            if (it.stack.getDestroySpeed(currentState) > currentDestroySpeed
//                && it.stack != player.heldItemMainhand
//                && (!getSilkDrop || EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it.stack) > 0)
            ) {
                context = BuildActivity.BuildContext.RESTOCK

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
                context = BuildActivity.BuildContext.RESTOCK

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
                    context = BuildActivity.BuildContext.RESTOCK

                    addSubActivities(SwapOrSwitchToSlot(it))
                    return false
                }
            }
        }

        return true
    }

    private fun SafeClientEvent.checkLiquids(): Boolean {
        var foundLiquid = false

        EnumFacing.values()
            .filter { it != EnumFacing.UP }
            .map { blockPos.offset(it) }
            .filter { world.getBlockState(it).isLiquid }
            .forEach {
                // ToDo: Don't add if exists
                PlaceBlock(it, BuildTools.defaultFillerMat.defaultState).apply {
                    context = BuildActivity.BuildContext.LIQUID
                    addSubActivities(this)
                }

                foundLiquid = true
            }

        return foundLiquid
    }

    private fun SafeClientEvent.updateDrops(currentState: IBlockState) {
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
                context = BuildActivity.BuildContext.NONE
                action = BuildActivity.BuildAction.NONE
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
    class BlockOutsideOfBoundsException(blockPos: BlockPos) : Exception("Block at (${blockPos.asString()}) is outside of world")
}