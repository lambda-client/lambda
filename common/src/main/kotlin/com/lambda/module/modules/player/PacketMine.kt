package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.DynamicESP
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
import net.minecraft.util.math.Vec3d
import java.awt.Color

object PacketMine : Module(
    name = "Packet Mine",
    description = "Mines blocks semi-automatically at a faster rate",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.General)

    private val breakThreshold by setting("Break Threshold", 0.7f, 0.0f..1.0f, 0.1f, "Breaks the selected block once the block breaking progress passes this value, 1 being 100%", visibility = { page == Page.General})
    private val range by setting("Range", 6, 3..6, 1, "The maximum distance between the players eye position and the center of the block", visibility = { page == Page.General })
    private val pauseWhileUsingItems by setting("Pause While Using Items", true, "Will prevent breaking while using items like eating or aiming a bow", visibility = { page == Page.General })
    //ToDo: Implement these settings
//    private val rotate by setting("Rotate", false, "Rotates the player to look at the current mining block", visibility = { page == Page.General })
    private val autoSwap by setting("Auto Force Swap", false, "Hard swaps to the best tool rather than silent swapping. This is often used on servers with a stricter anti-cheat system", visibility = { page == Page.General})
    private val validateBreak by setting("Validate Break", true, "Breaks blocks client side rather than waiting for a response from the server", visibility = { page == Page.General })
    private val timeoutDelay by setting("Timeout Delay", 0.20f, 0.00f..1.00f, 0.1f, "Will wait this amount of time (seconds) after the time to break for the block is complete before moving on", visibility = { page == Page.General && validateBreak })
    private val alternativePackets by setting("Alternative Packets", false, "Uses a different set of packets which tend to work better on servers using an anti-cheat like grim", visibility = { page == Page.General })

    private val queueBlocks by setting("Queue Blocks", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue }).apply { this.onValueSet { _, to -> if (!to) blockQueue.clear() } }
    private val reverseQueueOrder by setting("Reverse Queue Order", false, "Breaks the latest addition to the queue first", visibility = { page == Page.Queue && queueBlocks})

    private val reBreak by setting("Re-Break", true, "Automatically re-breaks the last mined block if it gets replaced", visibility = { page == Page.ReBreak})
    private val fastReBreak by setting("Fast Re-Break", false, "Re-breaks blocks instantly however could potentially cause ghost blocks", visibility = { page == Page.ReBreak && reBreak })


    private val breakingAnimation by setting("Breaking Animation", false, "Renders the block breaking animation like vanilla would to show progress", visibility = { page == Page.Render })
    private val renderMode by setting("Render Mode", RenderMode.Out, "The animation style of the renders", visibility = { page == Page.Render })
    private val renderSetting by setting("Render Setting", RenderSetting.Both, "The different ways to draw the renders", visibility = { page == Page.Render && renderMode.isEnabled() })

    private val fillColourMode by setting("Fill Mode", ColourMode.Dynamic, visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline })
    private val staticFillColour by setting("Static Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the static fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Static })
    private val startFillColour by setting("Start Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic })
    private val endFillColour by setting("End Fill Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic  })

    private val outlineColourMode by setting("Outline Mode", ColourMode.Dynamic, visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })
    private val staticOutlineColour by setting("Static Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the static outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Static })
    private val startOutlineColour by setting("Start Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic })
    private val endOutlineColour by setting("End Outline Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic  })
    private val outlineWidth by setting("Outline Width", 1f, 0f..3f, 0.1f, "the thickness of the outline", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })

    private enum class RenderMode {
        Out, In, InOut, OutIn, Static, None;

        fun isEnabled(): Boolean =
            this != None
    }

    private enum class RenderSetting {
        Both, Fill, Outline
    }

    private enum class ColourMode {
        Static, Dynamic
    }

    private enum class BreakState {
        Breaking, ReBreaking, AwaitingResponse
    }

    private enum class Page {
        General, Queue, ReBreak, Render
    }

    private var currentMiningBlock: BreakingContext? = null
    private val blockQueue = ArrayDeque<BlockPos>()
    private var returnSlot = -1
    private var swappedSlot = -1
    private var swapped = false

    //ToDo: Make work on CC

    init {
        listener<TickEvent.Pre> {
            currentMiningBlock?.apply {
                mineTicks++

                if (pauseWhileUsingItems && player.isUsingItem) return@listener

                val activeState = world.getBlockState(pos)
                if (activeState != state) state = activeState

                val empty = isStateEmpty(activeState)

                if (!empty) lastValidBestTool = getBestTool(activeState, pos)

                if (autoSwap) {
                    if (player.inventory.selectedSlot != swappedSlot) {
                        cancelSwap()
                    } else if (swappedSlot != lastValidBestTool) {
                        swapTo(lastValidBestTool)
                    }
                }

                currentBreakDelta = calcBreakDelta(state, pos, lastValidBestTool)

                if (renderMode.isEnabled()) updateRenders(currentBreakDelta)

                val miningProgress = mineTicks * currentBreakDelta

                if (breakingAnimation)
                    world.setBlockBreakingInfo(
                        player.id,
                        pos,
                        (miningProgress * (2 - breakThreshold) * 10).toInt().coerceAtMost(9)
                    )

                when (breakState) {
                    BreakState.Breaking -> {
                        if (miningProgress < breakThreshold) return@listener

                        timeCompleted = System.currentTimeMillis()

                        if (autoSwap) {
                            if (!swapped) {
                                swapTo(lastValidBestTool)
                            }
                            stopBreak(pos)
                        } else {
                            swapStopBreak(pos, lastValidBestTool)
                        }

                        if (validateBreak) {
                            breakState = BreakState.AwaitingResponse
                            return@listener
                        }

                        interaction.breakBlock(pos)

                        onBlockBreak()
                    }

                    BreakState.ReBreaking -> {
                        if (breakNextQueueBlock()) return@listener

                        if (!reBreak || isOutOfRange()) {
                            nullifyCurrentBreakingBlock()
                            return@listener
                        }

                        if (miningProgress < breakThreshold) {
                            if (autoSwap) swapTo(lastValidBestTool)
                            breakState = BreakState.Breaking
                            return@listener
                        }

                        if (!fastReBreak && empty) {
                            return@listener
                        }

                        if (autoSwap) {
                            if (!swapped) {
                                swapTo(lastValidBestTool)
                            }
                            stopBreak(pos)
                            if ((!validateBreak || empty) && swapped) swapToReturnSlot()
                        } else {
                            swapStopBreak(pos, lastValidBestTool)
                        }

                        checkClientBreak(false, pos)
                    }

                    BreakState.AwaitingResponse -> {
                        if (!validateBreak) {
                            checkClientBreak(false, pos)
                            nullifyCurrentBreakingBlock()
                            return@listener
                        }

                        if (System.currentTimeMillis() - timeCompleted < timeoutDelay * 1000) return@listener

                        if (breakNextQueueBlock()) return@listener

                        nullifyCurrentBreakingBlock()
                    }
                }
            }
        }

        listener<InteractionEvent.BlockAttack.Pre> {
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
                    || !isStateBroken(state, it.state)
                ) {
                    return@listener
                }

                checkClientBreak(true, pos)

                if (breakState == BreakState.ReBreaking) {
                    if (swapped) {
                        swapToReturnSlot()
                    }
                    return@listener
                }

                onBlockBreak()
            }
        }

        listener<RenderEvent.World> {
            currentMiningBlock?.apply {
                renderer.clear()
                buildRenders()
                renderer.upload()
            }
        }
    }

    private fun SafeContext.startBreaking(pos: BlockPos) {
        if (currentMiningBlock?.pos == pos || blockQueue.contains(pos)) return

        val state = world.getBlockState(pos)
        val bestTool = getBestTool(state, pos)

        val breakDelta = calcBreakDelta(world.getBlockState(pos), pos, bestTool)

        if (autoSwap) {
            swapTo(bestTool)
        } else {
            silentSwapTo(bestTool)
        }
        startBreak(pos)
        if (alternativePackets) {
            abortBreak(pos)
            stopBreak(pos)
        }

        if (breakDelta < breakThreshold) {
            if (!swapped) silentSwapTo(player.inventory.selectedSlot)
            currentMiningBlock = BreakingContext(pos, state, BreakState.Breaking, breakDelta)
            return
        }

        stopBreak(pos)
        if (!swapped) silentSwapTo(player.inventory.selectedSlot)

        currentMiningBlock = if (reBreak) {
            BreakingContext(pos, state, BreakState.ReBreaking, breakDelta)
        } else {
            if (!validateBreak) {
                null
            } else {
                BreakingContext(pos, state, BreakState.AwaitingResponse, breakDelta)
            }
        }

        currentMiningBlock?.apply {
            timeCompleted = System.currentTimeMillis()
            lastValidBestTool = bestTool

            if (!validateBreak && swapped) {
                swapToReturnSlot()
            }
        }

        checkClientBreak(false, pos)
    }

    private fun SafeContext.swapToReturnSlot() {
        if (!swapped || returnSlot == -1) return

        player.inventory.selectedSlot = returnSlot
        connection.sendPacket(UpdateSelectedSlotC2SPacket(returnSlot))
        returnSlot = -1
        swappedSlot = -1
        swapped = false

        return
    }

    private fun SafeContext.swapTo(slot: Int) {
        if (returnSlot == -1) {
            returnSlot = player.inventory.selectedSlot
        }
        player.inventory.selectedSlot = slot
        connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        swappedSlot = slot
        swapped = true
    }

    private fun cancelSwap() {
        returnSlot = -1
        swappedSlot = -1
        swapped = false
    }

    private fun SafeContext.silentSwapTo(slot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
    }

    private fun SafeContext.isOutOfRange() =
        player.eyePos.distanceTo(currentMiningBlock?.pos?.toCenterPos()) > range

    private fun SafeContext.isOutOfRange(vec: Vec3d) =
        player.eyePos.distanceTo(vec) > range

    private fun SafeContext.onBlockBreak() {
        currentMiningBlock?.apply {
            if (breakNextQueueBlock()) return

            if (reBreak && !isOutOfRange()) {
                if (swapped) swapToReturnSlot()
                breakState = BreakState.ReBreaking
                return
            }

            nullifyCurrentBreakingBlock()
        }
    }

    private fun isStateBroken(previousState: BlockState, activeState: BlockState) =
        activeState.isAir || (
                activeState.fluidState.fluid is WaterFluid
                        && previousState.properties.contains(Properties.WATERLOGGED)
                        && previousState.get(Properties.WATERLOGGED)
                )

    private fun isStateEmpty(state: BlockState) =
        state.isAir || (
                (!state.properties.contains(Properties.WATERLOGGED)
                        || !state.get(Properties.WATERLOGGED))
                        && !state.fluidState.isEmpty
                )

    private fun SafeContext.nullifyCurrentBreakingBlock() {
        currentMiningBlock?.apply {
            if (breakingAnimation) world.setBlockBreakingInfo(player.id, pos, 0)
        }

        if (swapped) swapToReturnSlot()

        currentMiningBlock = null
    }

    private fun SafeContext.checkClientBreak(packetReceiveBreak: Boolean, pos: BlockPos) {
        if (packetReceiveBreak == validateBreak) {
            interaction.breakBlock(pos)
        }
    }

    private fun shouldBePlacedInBlockQueue(pos: BlockPos): Boolean = queueBlocks
            && currentMiningBlock != null
            && currentMiningBlock?.breakState != BreakState.ReBreaking
            && currentMiningBlock?.pos != pos
            && !blockQueue.contains(pos)

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

            if (isOutOfRange(block.toCenterPos())) {
                blockQueue.remove(block)
                continue
            }

            return block
        }
    }

    private fun getLerpBox(box: Box, factor: Float): Box {
        val boxCenter = Box(box.center, box.center)
        when (renderMode) {
            RenderMode.Out -> {
                return lerp(boxCenter, box, factor.toDouble())
            }

            RenderMode.In -> {
                return lerp(box, boxCenter, factor.toDouble())
            }

            RenderMode.InOut -> {
                return if (factor >= 0.5f) {
                    lerp(boxCenter, box, (factor.toDouble() - 0.5) * 2)
                } else {
                    lerp(box, boxCenter, factor.toDouble() * 2)
                }
            }

            RenderMode.OutIn -> {
                return if (factor >= 0.5f) {
                    lerp(box, boxCenter, (factor.toDouble() - 0.5) * 2)
                } else {
                    lerp(boxCenter, box, factor.toDouble() * 2)
                }
            }

            else -> {
                return box
            }
        }
    }

    private data class BreakingContext(
        val pos: BlockPos,
        var state: BlockState,
        var breakState: BreakState,
        var currentBreakDelta: Float
    ) {
        val renderer = DynamicESP
        var mineTicks = 0
        var timeCompleted: Long = -1
        var previousBreakDelta = 0f
        var lastValidBestTool = -1

        var boxList = if (renderMode.isEnabled()) {
            state.getOutlineShape(mc.world, pos).boundingBoxes.toSet()
        } else {
            null
        }

        fun updateRenders(newBreakDelta: Float) {
            previousBreakDelta = currentBreakDelta
            currentBreakDelta = newBreakDelta
            if (renderMode.isEnabled())
                boxList = state.getOutlineShape(mc.world, pos).boundingBoxes.toSet()
        }

        fun SafeContext.buildRenders() {
            boxList?.forEach { box ->
                val previousFactor = (mineTicks - 1) * previousBreakDelta * (2 - breakThreshold)
                val nextFactor = mineTicks * currentBreakDelta * (2 - breakThreshold)
                val currentFactor = lerp(previousFactor, nextFactor, mc.tickDelta)

                val fillColour = if (fillColourMode == ColourMode.Dynamic) {
                    lerp(startFillColour, endFillColour, currentFactor.toDouble())
                } else {
                    staticFillColour
                }

                val outlineColour = if (outlineColourMode == ColourMode.Dynamic) {
                    lerp(startOutlineColour, endOutlineColour, currentFactor.toDouble())
                } else {
                    staticOutlineColour
                }

                val renderBox = if (renderMode != RenderMode.Static) {
                    getLerpBox(box, currentFactor).offset(pos)
                } else {
                    box.offset(pos)
                }

                val dynamicAABB = DynamicAABB()
                dynamicAABB.update(renderBox)

                if (renderSetting != RenderSetting.Outline) {
                    renderer.buildFilled(dynamicAABB, fillColour)
                }

                if (renderSetting != RenderSetting.Fill) {
                    renderer.buildOutline(dynamicAABB, outlineColour)
                }
            }
        }
    }

    private fun SafeContext.swapStopBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        stopBreak(pos)
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

    //Todo: Replace with task system

    private fun SafeContext.getBestTool(state: BlockState, pos: BlockPos): Int {
        val selectedSlot = player.inventory.selectedSlot
        var bestTool = selectedSlot
        var bestTimeToMine = calcBreakDelta(state, pos, selectedSlot)
        for (i in 0..8) {
            if (i == selectedSlot) continue
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