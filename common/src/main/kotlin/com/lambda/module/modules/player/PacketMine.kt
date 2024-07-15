package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.DynamicESP
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.visibilty.VisibilityChecker.findRotation
import com.lambda.module.Module
import com.lambda.module.modules.client.TaskFlow
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.MathUtils.lerp
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.screen.slot.SlotActionType
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

    private val breakThreshold by setting("Break Threshold", 0.70f, 0.00f..1.00f, 0.01f, "Breaks the selected block once the block breaking progress passes this value, 1 being 100%", visibility = { page == Page.General})
    private val range by setting("Range", 6, 3..6, 1, "The maximum distance between the players eye position and the center of the block", visibility = { page == Page.General })
    private val pauseWhileUsingItems by setting("Pause While Using Items", true, "Will prevent breaking while using items like eating or aiming a bow", visibility = { page == Page.General })
    private val resetOnManualSwap by setting("Reset On Manual Swap", false, "Resets the current breaking progress if you manual swap slot", visibility = { page == Page.General })
    private val validateBreak by setting("Validate Break", true, "Breaks blocks client side rather than waiting for a response from the server", visibility = { page == Page.General })
    private val timeoutDelay by setting("Timeout Delay", 0.20f, 0.00f..1.00f, 0.1f, "Will wait this amount of time (seconds) after the time to break for the block is complete before moving on", visibility = { page == Page.General && validateBreak })
    private val swingMode by setting("Swing Mode", SwingMode.None, "Swings the players hand to simulate vanilla breaking, usually used on stricter anticheats", visibility = { page == Page.General })
    private val swingOnManual by setting("Swing On Hit", true, "Swings when the player attacks a block", visibility = { page == Page.General })
    private val rotate by setting("Rotation Mode", RotationMode.None, "Changes the method used to make the player look at the current mining block", visibility = { page == Page.General })
    private val rotateReleaseDelay by setting("Rotation Release Delay", 2, 0..50, 1, "The number of ticks to wait before releasing the rotation", visibility = { page == Page.General && rotate.isEnabled() })
    private val autoSwap by setting("Swap Mode", SwapMode.StandardSilent, "Changes the swap method used. For example, silent swaps once at the beginning, and once at the end without updating client side, and constant swaps for the whole break", visibility = { page == Page.General})
    private val packets by setting("Packet Mode", PacketMode.Vanilla, "Chooses different packets to send for each mode", visibility = { page == Page.General })

    private val queueBlocks by setting("Queue Blocks", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue }).apply { this.onValueSet { _, to -> if (!to) blockQueue.clear() } }
    private val reverseQueueOrder by setting("Reverse Queue Order", false, "Breaks the latest addition to the queue first", visibility = { page == Page.Queue && queueBlocks})
    private val queueBreakDelay by setting("Break Delay", 0, 0..5, 1, "The delay (in ticks) after breaking a block to break the next queue block", visibility = { page == Page.Queue && queueBlocks })

    private val reBreak by setting("Re-Break", ReBreakMode.Standard, "The different modes for re-breaking the current block", visibility = { page == Page.ReBreak})
    private val reBreakDelay by setting("Re-Break Delay", 0, 0..10, 1, "The delay (in ticks) between attempting to re-breaking the block", visibility = { page == Page.ReBreak && (reBreak.isAutomatic() || reBreak.isFastAutomatic()) })
    private val emptyReBreakDelay by setting("Empty Re-Break Delay", 0, 0..10, 1, "The delay (in ticks) between attempting to re-break the block if the block is currently empty", visibility = { page == Page.ReBreak && reBreak.isFastAutomatic()})
    private val resetProgressOnBreak by setting("Reset Progress", false, "Resets the mining progress after breaking a block, mostly used on stricter servers", visibility = { page == Page.ReBreak && reBreak.isEnabled() })


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

    private enum class Page {
        General, Queue, ReBreak, Render
    }

    private enum class PacketMode {
        Vanilla, Grim, NCP
    }

    private enum class SwingMode {
        None, Start, Constant, StartAndEnd;

        fun isEnabled() =
            this != None

        fun isStart() =
            this == Start

        fun isConstant() =
            this == Constant

        fun isStartAndEnd() =
            this == StartAndEnd
    }

    private enum class RotationMode {
        None, Constant, StartAndEnd;

        fun isEnabled() =
            this != None

        fun isConstant() =
            this == Constant

        fun isStartAndEnd() =
            this == StartAndEnd
    }

    private enum class SwapMode {
        None, StandardSilent, NCPSilent, Constant, StartAndEnd;

        fun isEnabled() =
            this != None

        fun isSilent() =
            this == StandardSilent || this == NCPSilent

        fun isStandardSilent() =
            this == StandardSilent

        fun isNCPSilent() =
            this == NCPSilent

        fun isConstant() =
            this == Constant

        fun isStartAndEnd() =
            this == StartAndEnd
    }

    private enum class ReBreakMode {
        None, Standard, Automatic, FastAutomatic;

        fun isEnabled() =
            this != None

        fun isStandard() =
            this == Standard

        fun isAutomatic() =
            this == Automatic

        fun isFastAutomatic() =
            this == FastAutomatic
    }

    private enum class ProgressStage {
        StartPre, StartPost, PreTick, During, EndPre, EndPost, PacketReceiveBreak, TimedOut
    }

    private enum class RenderMode {
        None, Out, In, InOut, OutIn, Static;

        fun isEnabled() =
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

    private var currentMiningBlock: BreakingContext? = null
    private var lastNonEmptyState: BlockState? = null
    private val blockQueue = ArrayDeque<BlockPos>()
    private var queueBreakStartCounter = 0
    private var awaitingQueueBreak = false
    private var returnSlot = -1
    private var swappedSlot = -1
    private var swapped = false
    private var previousSelectedSlot = -1
    private var expectedRotation: Rotation? = null
    private var rotationPosition: BlockPos? = null
    private var pauseForRotation = false
    private var releaseRotateDelayCounter = 0
    private var reBreakDelayCounter = 0
    private var emptyReBreakDelayCounter = 0
    private var rotated = false
    private var waitingToReleaseRotation = false
    private var cancelNextSwing = false

    init {
        listener<InteractionEvent.BlockAttack.Pre> {
            it.cancel()
            if (swingOnManual) swingMainHand()
            if (!swingMode.isEnabled()) cancelNextSwing = true

            currentMiningBlock?.apply {
                if (it.pos != pos || breakState != BreakState.ReBreaking || !reBreak.isStandard()) return@apply

                runBetweenHandlers(ProgressStage.EndPre, ProgressStage.EndPost, pos, lastValidBestTool) {
                    packetStopBreak(pos)
                }

                onBlockBreak(false)
                return@listener
            }

            if (shouldBePlacedInBlockQueue(it.pos)) {
                blockQueue.add(it.pos)
                return@listener
            }

            startBreaking(it.pos)
        }

        listener<EntityEvent.SwingHand> {
            if (!cancelNextSwing)  return@listener

            cancelNextSwing = false
            it.cancel()
        }

        listener<TickEvent.Pre> {
            updateCounters()

            if (awaitingQueueBreak) {
                if (queueBreakStartCounter > 0) return@listener

                awaitingQueueBreak = false
                if (breakNextQueueBlock()) return@listener
            }

            currentMiningBlock?.apply {
                mineTicks++

                val activeState = world.getBlockState(pos)
                if (activeState != state) state = activeState

                val empty = isStateEmpty(activeState)
                if (!empty) lastNonEmptyState = state

                lastNonEmptyState?.let {
                    lastValidBestTool = getBestTool(it, pos)
                }

                runHandlers(ProgressStage.PreTick, pos, lastValidBestTool, empty)

                if (resetOnManualSwap
                    && !swapped
                    && player.inventory.selectedSlot != previousSelectedSlot
                    ) {
                    mineTicks = 0
                }
                previousSelectedSlot = player.inventory.selectedSlot

                if ((pauseWhileUsingItems && player.isUsingItem) || pauseForRotation)
                    return@listener

                currentBreakDelta = if (autoSwap.isEnabled()) {
                    calcBreakDelta(state, pos, lastValidBestTool)
                } else {
                    calcBreakDelta(state, pos, player.inventory.selectedSlot)
                }

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

                        runBetweenHandlers(ProgressStage.EndPre, ProgressStage.EndPost, pos, lastValidBestTool) {
                            packetStopBreak(pos)
                        }

                        onBlockBreak(false)
                    }

                    BreakState.ReBreaking -> {
                        if (miningProgress < breakThreshold) return@listener

                        if (isOutOfRange(pos.toCenterPos()) || !reBreak.isEnabled()) {
                            nullifyCurrentBreakingBlock()
                            return@listener
                        }

                        if (breakNextQueueBlock() || reBreak.isStandard()) return@listener

                        if (empty) {
                            if (emptyReBreakDelayCounter > 0) return@listener
                            emptyReBreakDelayCounter = emptyReBreakDelay
                        } else {
                            if (reBreakDelayCounter > 0) return@listener
                            reBreakDelayCounter = reBreakDelay
                        }

                        if (!reBreak.isFastAutomatic() && empty) {
                            return@listener
                        }

                        runBetweenHandlers(ProgressStage.EndPre, ProgressStage.EndPost, pos, lastValidBestTool, empty) {
                            packetStopBreak(pos)
                        }

                        onBlockBreak(false)
                    }

                    BreakState.AwaitingResponse -> {
                        if (!validateBreak) {
                            runHandlers(ProgressStage.EndPost, pos, lastValidBestTool)
                            onBlockBreak(false)
                            return@listener
                        }

                        if (System.currentTimeMillis() - timeCompleted < timeoutDelay * 1000) return@listener

                        if (breakNextQueueBlock()) return@listener

                        runHandlers(ProgressStage.TimedOut, pos, lastValidBestTool)

                        nullifyCurrentBreakingBlock()
                    }
                }
            }
        }

        listener<WorldEvent.BlockUpdate> {
            currentMiningBlock?.apply {
                if (it.pos != pos || !isStateBroken(state, it.state)) {
                    return@listener
                }

                runHandlers(ProgressStage.PacketReceiveBreak, pos, lastValidBestTool)

                onBlockBreak(true)
            }
        }

        listener<RotationEvent.Update> {
            if (!rotate.isEnabled()) return@listener

            lastNonEmptyState?.let { state ->
                rotationPosition?.let { pos ->
                    val boxList = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
                    val rotationContext = findRotation(boxList, TaskFlow.rotation, TaskFlow.interact, emptySet(), verify = { true })
                    rotationContext?.let { context ->
                        it.context = context
                        expectedRotation = context.rotation
                    }
                } ?: run {
                    expectedRotation = null
                }
            }
        }

        listener<RotationEvent.Post> {
            if (!rotate.isEnabled()) return@listener

            expectedRotation?.apply {
                if (it.context.rotation != expectedRotation)
                    pauseForRotation = true
            } ?: run {
                pauseForRotation = false
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
        lastNonEmptyState = state

        val breakDelta = calcBreakDelta(world.getBlockState(pos), pos, bestTool)
        val instaBreak = breakDelta >= breakThreshold

        previousSelectedSlot = player.inventory.selectedSlot

        runBetweenHandlers(ProgressStage.StartPre, ProgressStage.StartPost, pos, bestTool, instaBreak) {
            packetStartBreak(pos)

            currentMiningBlock = BreakingContext(pos, state, BreakState.Breaking, breakDelta, bestTool)

            if (!instaBreak) return@runBetweenHandlers

            packetStopBreak(pos)
            if (reBreak.isEnabled()) packetStartBreak(pos)

            onBlockBreak(false)
        }
    }

    private fun SafeContext.runBetweenHandlers(
        preStage: ProgressStage,
        postStage: ProgressStage,
        pos: BlockPos,
        bestTool: Int,
        empty: Boolean = false,
        instaBroken: Boolean = false,
        task: Runnable
    ) {
        runHandlers(preStage, pos, bestTool, empty, instaBroken)
        task.run()
        runHandlers(postStage, pos, bestTool, empty, instaBroken)
    }

    private fun SafeContext.runHandlers(
        progressStage: ProgressStage,
        pos: BlockPos,
        bestTool: Int,
        empty: Boolean = false,
        instaBroken: Boolean = false
    ) {
        handleRotations(progressStage, pos, empty, instaBroken)
        handleAutoSwap(progressStage, bestTool, empty, instaBroken)
        handleSwing(progressStage)
    }

    private fun handleRotations(progressStage: ProgressStage, pos: BlockPos, empty: Boolean = false, instaBroken: Boolean = false) {
        when (progressStage) {
            ProgressStage.PreTick -> if (rotated && rotate.isStartAndEnd()) checkReleaseRotation()

            ProgressStage.StartPre,
            ProgressStage.EndPre -> if (rotate.isEnabled() && !rotated) rotateTo(pos)

            ProgressStage.During -> if (rotate.isConstant()) rotateTo(pos)

            ProgressStage.StartPost -> if (instaBroken && !validateBreak) checkReleaseRotation()

            ProgressStage.EndPost -> if (!validateBreak || empty) checkReleaseRotation()

            ProgressStage.PacketReceiveBreak -> if (rotated) checkReleaseRotation()

            ProgressStage.TimedOut -> checkReleaseRotation()
        }
    }

    private fun SafeContext.handleAutoSwap(progressStage: ProgressStage, bestTool: Int, empty: Boolean = false, instaBroken: Boolean = false) {
        if (!autoSwap.isEnabled()) return

        when (progressStage) {
            ProgressStage.PreTick -> {
                if (!swapped || autoSwap.isSilent()) return
                when {
                    player.inventory.selectedSlot != swappedSlot -> cancelSwap()

                    autoSwap.isConstant() -> {
                        if (swappedSlot != bestTool) swapTo(bestTool)
                    }

                    autoSwap.isStartAndEnd() -> returnToOriginalSlot()
                }
            }

            ProgressStage.StartPre,
            ProgressStage.EndPre -> {
                if (!swapped) swapTo(bestTool)
            }

            ProgressStage.During -> if (autoSwap.isConstant()) swapTo(bestTool)

            ProgressStage.StartPost -> {
                if (!swapped) return

                if ((instaBroken && !validateBreak)
                    || autoSwap.isSilent()
                    ) {
                    returnToOriginalSlot()
                }
            }

            ProgressStage.EndPost -> {
                if (!swapped) return

                if ((!validateBreak || empty)
                    || autoSwap.isSilent()
                    ) {
                    returnToOriginalSlot()
                }
            }

            ProgressStage.PacketReceiveBreak -> if (swapped && !autoSwap.isSilent()) returnToOriginalSlot()

            ProgressStage.TimedOut -> if (swapped) returnToOriginalSlot()
        }
    }

    private fun SafeContext.handleSwing(progressStage: ProgressStage) {
        when (progressStage) {
            ProgressStage.PreTick -> {
                currentMiningBlock?.apply {
                    if (breakState != BreakState.ReBreaking
                        && swingMode.isConstant()
                        ) {
                        swingMainHand()
                    }
                }
            }

            ProgressStage.During -> if (swingMode.isConstant()) swingMainHand()

            ProgressStage.StartPre,
            ProgressStage.StartPost -> if (swingMode.isStart() || swingMode.isStartAndEnd()) swingMainHand()

            ProgressStage.EndPre,
            ProgressStage.EndPost -> if (swingMode.isStartAndEnd() || swingMode.isConstant()) swingMainHand()

            ProgressStage.PacketReceiveBreak,
            ProgressStage.TimedOut -> {}
        }
    }

    private fun SafeContext.swingMainHand() =
        player.swingHand(Hand.MAIN_HAND)

    private fun updateCounters() {
        if (rotated && waitingToReleaseRotation) {
            releaseRotateDelayCounter--

            if (releaseRotateDelayCounter <= 0) {
                waitingToReleaseRotation = false
                rotationPosition = null
                rotated = false
            }
        }
        if (reBreakDelayCounter > 0) {
            reBreakDelayCounter--
        }
        if (emptyReBreakDelayCounter > 0) {
            emptyReBreakDelayCounter--
        }
        if (queueBreakStartCounter > 0) {
            queueBreakStartCounter--
        }
    }

    private fun rotateTo(pos: BlockPos) {
        waitingToReleaseRotation = false
        releaseRotateDelayCounter = rotateReleaseDelay
        rotationPosition = pos
        rotated = true
    }

    private fun checkReleaseRotation() {
        if (waitingToReleaseRotation || !rotated) return

        if (releaseRotateDelayCounter <= 0) {
            rotationPosition = null
            rotated = false
        } else {
            waitingToReleaseRotation = true
        }
    }

    private fun SafeContext.swapTo(slot: Int) {
        if (returnSlot == -1) {
            returnSlot = player.inventory.selectedSlot
        }
        if (autoSwap.isSilent()) {
            silentSwapTo(slot, false)
        } else {
            player.inventory.selectedSlot = slot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        }
        swappedSlot = slot
        swapped = true
    }

    private fun SafeContext.silentSwapTo(slot: Int, returningToOriginalSlot: Boolean) {
        if (autoSwap.isStandardSilent()) {
            connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        } else {
            val screenHandler = player.playerScreenHandler
            var itemStack = player.mainHandStack
            var newSlot = slot

            if (returningToOriginalSlot) {
                newSlot = swappedSlot
                itemStack = player.inventory.getStack(swappedSlot)
            }

            connection.sendPacket(
                ClickSlotC2SPacket(
                    screenHandler.syncId,
                    screenHandler.revision,
                    newSlot + 36,
                    player.inventory.selectedSlot,
                    SlotActionType.SWAP,
                    itemStack,
                    Int2ObjectArrayMap()
                )
            )
        }
    }

    private fun SafeContext.returnToOriginalSlot() {
        if (!swapped || returnSlot == -1) return

        if (!autoSwap.isSilent()) {
            player.inventory.selectedSlot = returnSlot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(returnSlot))
        } else {
            silentSwapTo(returnSlot, true)
        }
        returnSlot = -1
        swappedSlot = -1
        swapped = false

        return
    }

    private fun cancelSwap() {
        returnSlot = -1
        swappedSlot = -1
        swapped = false
    }

    private fun SafeContext.isOutOfRange(vec: Vec3d) =
        player.eyePos.distanceTo(vec) > range

    private fun SafeContext.onBlockBreak(packetReceiveBreak: Boolean) {
        currentMiningBlock?.apply {
            checkClientSideBreak(packetReceiveBreak, pos)

            timeCompleted = System.currentTimeMillis()

            if (validateBreak && breakState == BreakState.Breaking) {
                breakState = BreakState.AwaitingResponse
                return
            }

            queueBreakStartCounter = queueBreakDelay
            if (breakNextQueueBlock()) return

            if (reBreak.isEnabled() && !isOutOfRange(pos.toCenterPos())) {
                if (breakState != BreakState.ReBreaking) {
                    breakState = BreakState.ReBreaking
                }

                if (resetProgressOnBreak) mineTicks = 0
                return
            }

            nullifyCurrentBreakingBlock()
        }
    }

    private fun SafeContext.nullifyCurrentBreakingBlock() {
        currentMiningBlock?.apply {
            if (breakingAnimation) world.setBlockBreakingInfo(player.id, pos, -1)
        }

        currentMiningBlock = null
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

    private fun SafeContext.checkClientSideBreak(packetReceiveBreak: Boolean, pos: BlockPos) {
        if (packetReceiveBreak == validateBreak) interaction.breakBlock(pos)
    }

    private fun shouldBePlacedInBlockQueue(pos: BlockPos): Boolean =
        queueBlocks
            && currentMiningBlock != null
            && currentMiningBlock?.breakState != BreakState.ReBreaking
            && currentMiningBlock?.pos != pos
            && !blockQueue.contains(pos)

    private fun SafeContext.breakNextQueueBlock(): Boolean {
        if (!queueBlocks) return false

        filterBlockQueueUntilNextPossible()?.apply {
            if (queueBreakStartCounter <= 0) {
                blockQueue.remove(this)
                startBreaking(this)
            } else {
                currentMiningBlock = null
                awaitingQueueBreak = true
            }
            return true
        }

        return false
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

    private fun getNextUncheckedQueueBlock(): BlockPos? {
        return if (reverseQueueOrder) {
            blockQueue.lastOrNull()
        } else {
            blockQueue.firstOrNull()
        }
    }

    private data class BreakingContext(
        val pos: BlockPos,
        var state: BlockState,
        var breakState: BreakState,
        var currentBreakDelta: Float,
        var lastValidBestTool: Int
    ) {
        val renderer = DynamicESP
        var mineTicks = 0
        var timeCompleted: Long = -1
        var previousBreakDelta = 0f

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
    }

    private fun SafeContext.packetStartBreak(pos: BlockPos) {
        startBreak(pos)
        if (packets != PacketMode.Vanilla) {
            abortBreak(pos)
        }
        if (packets == PacketMode.Grim) {
            packetStopBreak(pos)
        }
    }

    private fun SafeContext.packetStopBreak(pos: BlockPos) {
        stopBreak(pos)
        if (packets == PacketMode.NCP) {
            abortBreak(pos)
            startBreak(pos)
            stopBreak(pos)
        }
    }

    private fun SafeContext.startBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP))
    }

    private fun SafeContext.stopBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP))
    }

    private fun SafeContext.abortBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.UP))
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