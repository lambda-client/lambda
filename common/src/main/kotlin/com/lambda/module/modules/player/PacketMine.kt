package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.result.Drawable
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.MathUtils.lerp
import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import java.awt.Color

object PacketMine : Module(
    name = "Packet Mine",
    description = "Mines blocks semi-automatically at a faster rate",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.General)

    private val breakSpeed by setting("Break Speed", 1.0, 0.0..1.0, 0.1, "Breaks the selected block at the time to break of the block multiplied by this value", visibility = { page == Page.General})
    private val pauseWhileUsingItems by setting("Pause While Using Items", true, "Will prevent breaking while using items like eating or aiming a bow", visibility = { page == Page.General })
    //ToDo: Implement these settings
//    private val rotate by setting("Rotate", false, "Rotates the player to look at the current mining block", visibility = { page == Page.General })
//    private val autoSwap by setting("Auto Force Swap", false, "Hard swaps to the best tool rather than silent swapping. This is often used on servers with a stricter anti-cheat system", visibility = { page == Page.General})
    private val validateBreak by setting("Validate Break", true, "Breaks blocks client side rather than waiting for a response from the server", visibility = { page == Page.General })
    private val timeoutDelay by setting("Timeout Delay", 0.20, 0.00..1.00, 0.1, "Will wait this amount of time (seconds) after the time to break for the block is complete before moving on", visibility = { page == Page.General && validateBreak })
    private val alternativePackets by setting("Alternative Packets", false, "Uses a different set of packets which tend to work better on servers using an anti-cheat like grim", visibility = { page == Page.General })

    private val queueBlocks by setting("Queue Blocks", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue }).apply { this.onValueSet { _, to -> if (!to) blockQueue.clear() } }
    private val reverseQueueOrder by setting("Reverse Queue Order", false, "Breaks the latest addition to the queue first", visibility = { page == Page.Queue && queueBlocks})

    private val reBreak by setting("Re-Break", true, "Automatically re-breaks the last mined block if it gets replaced", visibility = { page == Page.ReBreak})
    private val fastReBreak by setting("Fast Re-Break", false, "Re-breaks blocks instantly however could potentially cause ghost blocks", visibility = { page == Page.ReBreak && reBreak })

    private val breakingAnimation by setting("Breaking Animation", false, "Renders the block breaking animation like vanilla would to show progress", visibility = { page == Page.Render })
    private val renderMode by setting("Render Mode", RenderMode.InOut, "Renders a box with size corresponding to amount broken", visibility = { page == Page.Render })
    private val renderColor by setting("Render Colour", Color.RED, "The colour used to render the breaking box", visibility = { page == Page.Render })


    private var currentMiningBlock: BreakingContext? = null
    private var ignorePacketSend = false
    private val blockQueue = ArrayDeque<BlockPos>()

    //ToDo: Make work on CC

    init {
        listener<TickEvent.Pre> {
            currentMiningBlock?.apply {
                mineTicks++

                if (pauseWhileUsingItems && player.isUsingItem) return@listener

                val activeState = world.getBlockState(pos)
                if (activeState != state) state = activeState
                val bestTool = getBestTool(activeState, pos)

                currentBreakDelta = calcBreakDelta(state, pos, bestTool)

                if (renderMode.isEnabled()) updateBoxList(currentBreakDelta)

                val miningProgress = mineTicks * currentBreakDelta

                if (breakingAnimation) world.setBlockBreakingInfo(player.id, pos, (miningProgress * (2 - breakSpeed) * 10).toInt().coerceAtMost(9))

                when (breakState) {
                    BreakState.Breaking -> {
                        if (miningProgress < breakSpeed) return@listener

                        timeCompleted = System.currentTimeMillis()
                        swapStopBreak(pos, bestTool)

                        if (validateBreak) {
                            breakState = BreakState.AwaitingResponse
                            return@listener
                        }

                        interaction.breakBlock(pos)

                        onBlockBreak()
                    }
                    BreakState.ReBreaking -> {
                        if (breakNextQueueBlock()) return@listener

                        if (player.eyePos.distanceTo(pos.toCenterPos()) > 6) {
                            nullifyCurrentBreakingBlock()
                            return@listener
                        }

                        if (miningProgress < breakSpeed) return@listener

                        if (!fastReBreak
                            && (activeState.isAir || (!activeState.fluidState.isEmpty && !(activeState.properties.contains(Properties.WATERLOGGED) && activeState.get(Properties.WATERLOGGED))))) {
                            return@listener
                        }

                        swapStopBreak(pos, bestTool)

                        checkClientBreak(false, pos)
                    }
                    BreakState.AwaitingResponse -> {
                        if (!validateBreak) return@listener

                        if (System.currentTimeMillis() - timeCompleted < timeoutDelay * 1000) return@listener

                        if (breakNextQueueBlock()) return@listener

                        nullifyCurrentBreakingBlock()
                    }
                }
            }
        }

        listener<InteractionEvent.AttackBlock> {
            it.cancel()
            player.swingHand(Hand.MAIN_HAND)

            if (shouldBePlacedInBlockQueue(it.pos)) {
                blockQueue.add(it.pos)
                return@listener
            }

            startBreaking(it.pos)
        }

        listener<WorldEvent.BlockUpdate> {
            currentMiningBlock?.apply {
                if (it.pos != pos
                    || if (state.properties.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) it.state.fluidState.fluid !is WaterFluid
                    else !it.state.isAir) {
                    return@listener
                }

                checkClientBreak(true, pos)

                if (breakState == BreakState.ReBreaking) return@listener

                onBlockBreak()
            }
        }

        listener<RenderEvent.BlockESP> {
            currentMiningBlock?.apply {
                buildRenderer()
            }
        }
    }

    private fun SafeContext.startBreaking(pos: BlockPos) {
        if (currentMiningBlock?.pos == pos || blockQueue.contains(pos)) return

        val state = world.getBlockState(pos)
        val bestTool = getBestTool(state, pos)

        swapStartPacketBreak(pos, bestTool)

        val breakDelta = calcBreakDelta(world.getBlockState(pos), pos, bestTool)

        if (breakDelta > breakSpeed) {
            if (reBreak) {
                swapStartPacketBreak(pos, bestTool)
                currentMiningBlock = BreakingContext(pos, state, BreakState.ReBreaking, breakDelta)
            } else {
                currentMiningBlock = if (!validateBreak) {
                    null
                } else {
                    BreakingContext(pos, state, BreakState.AwaitingResponse, breakDelta)
                }
            }
            currentMiningBlock?.timeCompleted = System.currentTimeMillis()

            checkClientBreak(false, pos)
        }

        currentMiningBlock = BreakingContext(pos, state, BreakState.Breaking, breakDelta)
    }

    private fun SafeContext.onBlockBreak() {
        currentMiningBlock?.apply {
            if (breakNextQueueBlock()) return

            if (reBreak && player.eyePos.distanceTo(pos.toCenterPos()) < 6) {
                breakState = BreakState.ReBreaking
                return
            }

            nullifyCurrentBreakingBlock()
        }
    }

    private fun SafeContext.nullifyCurrentBreakingBlock() {
        currentMiningBlock?.apply {
            if (breakingAnimation) world.setBlockBreakingInfo(player.id, pos, 0)
        }
        currentMiningBlock = null
    }

    private fun SafeContext.checkClientBreak(packetReceiveBreak: Boolean, pos: BlockPos) {
        if (packetReceiveBreak == validateBreak) {
            interaction.breakBlock(pos)
        }
    }

    private fun shouldBePlacedInBlockQueue(pos: BlockPos): Boolean {
        return !(!queueBlocks
                || currentMiningBlock == null
                || currentMiningBlock?.breakState == BreakState.ReBreaking
                || currentMiningBlock?.pos == pos
                || blockQueue.contains(pos))
    }

    private fun SafeContext.breakNextQueueBlock(): Boolean {
        if (!queueBlocks) return false

        filterBlockQueueUntilNextPossible()?.apply {
            blockQueue.remove(this)
            startBreaking(this)
            return true
        }

        return false
    }

    private fun getNextUncheckedQueueBlock(): BlockPos? {
        return if (reverseQueueOrder) {
            blockQueue.lastOrNull()
        } else {
            blockQueue.firstOrNull()
        }
    }

    private fun SafeContext.filterBlockQueueUntilNextPossible(): BlockPos? {
        while (true) {
            val block = getNextUncheckedQueueBlock() ?: return null

            if (player.eyePos.distanceTo(block.toCenterPos()) > 6) {
                blockQueue.remove(block)
                continue
            }

            return block
        }
    }

    private fun getLerp(box: Box, factor: Float): Box {
        return if (renderMode == RenderMode.InOut) {
            lerp(Box(box.center, box.center), box, factor.toDouble())
        } else {
            lerp(box, Box(box.center, box.center), factor.toDouble())
        }
    }

    private fun SafeContext.swapStartPacketBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        startBreak(pos)
        if (alternativePackets) {
            abortBreak(pos)
            stopBreak(pos)
        }
        ignorePacketSend = false
        connection.sendPacket(UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot))
    }

    private fun SafeContext.swapStopBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        stopBreak(pos)
        ignorePacketSend = false
        connection.sendPacket(UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot))
    }

    private fun SafeContext.startBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.stopBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.abortBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private data class BreakingContext(
        val pos: BlockPos,
        var state: BlockState,
        var breakState: BreakState,
        var currentBreakDelta: Float
    ) : Drawable {
        var mineTicks = 0
        var timeCompleted: Long = -1

        var previousBreakDelta = 0f

        var boxList = if (renderMode.isEnabled()) {
            state.getCollisionShape(mc.world, pos).boundingBoxes.toSet()
        } else {
            null
        }

        fun updateBoxList(newBreakDelta: Float) {
            previousBreakDelta = currentBreakDelta
            currentBreakDelta = newBreakDelta
            if (renderMode.isEnabled())
                boxList = state.getCollisionShape(mc.world, pos).boundingBoxes.toSet()
        }

        override fun SafeContext.buildRenderer() {
            boxList?.forEach { box ->
                withBox(lerp(
                    getLerp(box, (mineTicks - 1) * previousBreakDelta * (2 - breakSpeed.toFloat())).offset(pos)
                    , getLerp(box, mineTicks * currentBreakDelta * (2 - breakSpeed.toFloat())).offset(pos)
                    , mc.tickDelta.toDouble())
                    , renderColor)
            }
        }
    }

    private enum class RenderMode {
        InOut, OutIn, None;
        fun isEnabled(): Boolean =
            this != None
    }

    private enum class BreakState {
        Breaking, ReBreaking, AwaitingResponse
    }

    private enum class Page {
        General, Queue, ReBreak, Render
    }

    //Todo: Replace with task system

    private fun SafeContext.getBestTool(state: BlockState, pos: BlockPos): Int {
        var bestTool = -1
        var bestTimeToMine = 0f
        for (i in 0..8) {
            val currentToolsTimeToMine = calcBreakDelta(state, pos, i)
            if (currentToolsTimeToMine > bestTimeToMine) {
                bestTimeToMine = currentToolsTimeToMine
                bestTool = i
            }
        }
        return bestTool
    }

    private fun SafeContext.calcBreakDelta(state: BlockState, pos: BlockPos, toolSlot: Int): Float {
        val f: Float = state.getHardness(world, pos)
        if (f == -1.0f) {
            return 0.0f
        } else {
            val i = if (!state.isToolRequired || player.inventory.getStack(toolSlot).isSuitableFor(state)) 30 else 100
            return getBlockBreakingSpeed(state, toolSlot) / f / i.toFloat()
        }
    }

    private fun SafeContext.getBlockBreakingSpeed(state: BlockState, toolSlot: Int): Float {
        var f: Float = player.inventory.getStack(toolSlot).getMiningSpeedMultiplier(state)
        if (f > 1.0f) {
            val itemStack: ItemStack = player.inventory.getStack(toolSlot)
            val i = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, itemStack)
            if (i > 0 && !itemStack.isEmpty) {
                f += (i * i + 1).toFloat()
            }
        }

        if (StatusEffectUtil.hasHaste(player)) {
            f *= 1.0f + (StatusEffectUtil.getHasteAmplifier(player) + 1).toFloat() * 0.2f
        }

        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            val g = when (player.getStatusEffect(StatusEffects.MINING_FATIGUE)?.amplifier) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                3 -> 8.1E-4f
                else -> 8.1E-4f
            }
            f *= g
        }

        if (player.isSubmergedIn(FluidTags.WATER)
            && !EnchantmentHelper.hasAquaAffinity(player)
        ) {
            f /= 5.0f
        }

        if (!player.isOnGround) {
            f /= 5.0f
        }

        return f
    }
}