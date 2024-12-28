/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.DynamicESP
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.findRotation
import com.lambda.module.Module
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.lerp
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.util.function.Supplier

object PacketMine : Module(
    name = "Packet Mine",
    description = "Mines blocks semi-automatically at a faster rate",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.General)

    private val breakThreshold by setting("Break Threshold", 0.70f, 0.00f..1.00f, 0.01f, "Breaks the selected block once the block breaking progress passes this value, 1 being 100%", visibility = { page == Page.General})
    private val breakMode by setting("Break Mode", BreakMode.Total, "Changes the way break amount is added up. Total will choose the best tool and act as if its been using it the whole time while additive will add progress throughout the break", visibility = { page == Page.General })
    private val strict by setting("Strict", false, "Resets the breaking progress at various stages to bypass generally stricter anti-cheats", visibility = { page == Page.General })
    private val range by setting("Range", 6.0f, 3.0f..6.0f, 0.1f, "The maximum distance between the players eye position and the center of the block", " blocks", visibility = { page == Page.General })
    private val doubleBreak by setting("Double Break", false, "This exploit only works on non strict servers or servers that run grim. It breaks two blocks at once", visibility = { page == Page.General })
    private val pauseWhileUsingItems by setting("Pause While Using Items", true, "Will prevent breaking while using items like eating or aiming a bow", visibility = { page == Page.General })
    private val validateBreak by setting("Validate Break", true, "Breaks blocks client side rather than waiting for a response from the server", visibility = { page == Page.General })
    private val timeoutDelay by setting("Timeout Delay", 0.20f, 0.00f..1.00f, 0.1f, "Will wait this amount of time (seconds) after the time to break for the block is complete before moving on", " seconds", visibility = { page == Page.General && validateBreak })
    private val swingMode by setting("Swing Mode", ModeOptions.None, "Swings the players hand to simulate vanilla breaking, usually used on stricter anti-cheats", visibility = { page == Page.General })
    private val swingOnManual by setting("Manual Swing", true, "Swings when the player attacks a block", visibility = { page == Page.General })
    private val rotate by setting("Rotation Mode", ModeOptions.None, "Changes the method used to make the player look at the current mining block", visibility = { page == Page.General })
    private val rayCast by setting("Raycast", false, "Checks if the player is directly looking at the block rather than allowing through walls", visibility = { page == Page.General && rotate.isEnabled() })
    private val rotateReleaseDelay by setting("Rotation Release Delay", 1, 0..5, 1, "The number of ticks to wait before releasing the rotation", " ticks", visibility = { page == Page.General && rotate.isEnabled() })
    private val swapMethod by setting("Swap Method", SwapMethod.StandardSilent, "Changes the swap method used. For example, silent swaps once at the beginning, and once at the end without updating client side, and constant swaps for the whole break", visibility = { page == Page.General})
    private val swapMode by setting("Swap Mode", SwapMode.StartAndEnd, "The different times to swap to the best tool", visibility = { page == Page.General && swapMethod.isEnabled()})
    private val packets by setting("Packet Mode", PacketMode.Vanilla, "Chooses different packets to send for each mode", visibility = { page == Page.General })

    private val reBreak by setting("Re-Break", ReBreakMode.Standard, "The different modes for re-breaking the current block", visibility = { page == Page.ReBreak})
    private val reBreakDelay by setting("Re-Break Delay", 0, 0..10, 1, "The delay between attempting to re-breaking the block", "ticks", visibility = { page == Page.ReBreak && (reBreak.isAutomatic() || reBreak.isFastAutomatic()) })
    private val emptyReBreakDelay by setting("Empty Re-Break Delay", 0, 0..10, 1, "The delay between attempting to re-break the block if the block is currently empty", " ticks", visibility = { page == Page.ReBreak && reBreak.isFastAutomatic()})
    private val renderIfEmpty by setting("Render If Empty", false, "Draws the renders even if the re-break position is empty in the world", visibility = { page == Page.ReBreak && reBreak.isEnabled() })

    private val queueBlocks by setting("Queue Blocks", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue }).apply { this.onValueSet { _, to -> if (!to) blockQueue.clear() } }
    private val reverseQueueOrder by setting("Reverse Queue Order", false, "Breaks the latest addition to the queue first", visibility = { page == Page.Queue && queueBlocks})
    private val queueBreakDelay by setting("Break Delay", 0, 0..5, 1, "The delay after breaking a block to break the next queue block", " ticks", visibility = { page == Page.Queue && queueBlocks })


    private val breakingAnimation by setting("Breaking Animation", false, "Renders the block breaking animation like vanilla would to show progress", visibility = { page == Page.BlockRender })
    private val renderMode by setting("BlockRender Mode", RenderMode.Out, "The animation style of the renders", visibility = { page == Page.BlockRender })
    private val renderSetting by setting("BlockRender Setting", RenderSetting.Both, "The different ways to draw the renders", visibility = { page == Page.BlockRender && renderMode.isEnabled() })

    private val fillColourMode by setting("Fill Mode", ColourMode.Dynamic, visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Outline })
    private val staticFillColour by setting("Static Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the static fill of the box faces", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Static })
    private val startFillColour by setting("Start Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start fill of the box faces", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic })
    private val endFillColour by setting("End Fill Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end fill of the box faces", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic  })

    private val outlineColourMode by setting("Outline Mode", ColourMode.Dynamic, visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })
    private val staticOutlineColour by setting("Static Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the outline of the box", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Static })
    private val startOutlineColour by setting("Start Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start outline of the box", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic })
    private val endOutlineColour by setting("End Outline Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end outline of the box", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic  })
    private val outlineWidth by setting("Outline Width", 1f, 0f..3f, 0.1f, "the thickness of the outline", visibility = { page == Page.BlockRender && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })

    private val renderQueueMode by setting("Render mode", RenderQueueMode.Cube, "The render type for queue blocks", visibility = { page == Page.QueueRender })
    private val renderQueueSetting by setting("Render Setting", RenderSetting.Both, "The style to render queue blocks", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() })
    private val renderQueueSize by setting("Render Size", 0.3f, 0f..1f, 0.01f, "The scale of the queue render blocks", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() })

    private val queueFillColourMode by setting("Fill Mode", ColourMode.Dynamic, visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Outline })
    private val queueStaticFillColour by setting("Static Fill Colour", Color(1f, 0f, 0f, 0.2f), "The colour used to render the faces for queue blocks", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Outline && queueFillColourMode == ColourMode.Static })
    private val queueStartFillColour by setting("Start Fill Colour", Color(1f, 0f, 0f, 0.2f), "The colour to render the faces for queue blocks closer to being broken next", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Outline && queueFillColourMode == ColourMode.Dynamic })
    private val queueEndFillColour by setting("End Fill Colour", Color(1f, 1f, 0f, 0.2f), "the colour to render the faces for queue blocks closer to the end of the queue", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Outline && queueFillColourMode == ColourMode.Dynamic })

    private val queueOutlineColourMode by setting("Outline Mode", ColourMode.Dynamic, visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Fill })
    private val queueStaticOutlineColour by setting("Static Outline Colour", Color(1f, 0f, 0f), "The colour used to render the outline for queue blocks", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Fill && queueOutlineColourMode == ColourMode.Static })
    private val queueStartOutlineColour by setting("Start Outline Colour", Color(1f, 0f, 0f), "The colour to render the outline for queue blocks closer to being broken next", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Fill && queueOutlineColourMode == ColourMode.Dynamic })
    private val queueEndOutlineColour by setting("End Outline Colour", Color(1f, 1f, 0f), "the colour to render the outline for queue blocks closer to the end of the queue", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderQueueSetting != RenderSetting.Fill && queueOutlineColourMode == ColourMode.Dynamic })
    private val queueOutlineWidth by setting("Outline Width", 1f, 0f..3f, 0.1f, "The thickness of the outline used on queue blocks", visibility = { page == Page.QueueRender && renderQueueMode.isEnabled() && renderSetting != RenderSetting.Fill } )


    private enum class Page {
        General, ReBreak, Queue, BlockRender, QueueRender
    }

    @Suppress("UNUSED")
    private enum class BreakMode {
        Total, Additive;

        fun isTotal() =
            this == Total
    }

    private enum class PacketMode {
        Vanilla, Grim, NCP
    }

    @Suppress("UNUSED")
    private enum class SwapMethod {
        None, StandardSilent, NCPSilent, Vanilla;

        fun isEnabled() =
            this != None

        fun isSilent() =
            this == StandardSilent || this == NCPSilent

        fun isStandardSilent() =
            this == StandardSilent

        fun isNCPSilent() =
            this == NCPSilent
    }

    private enum class SwapMode {
        StartAndEnd, Start, End, Constant;

        fun isConstant() =
            this == Constant

        fun isStart() =
            this == Start

        fun isEnd() =
            this == End

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

    private enum class ModeOptions {
        None, StartAndEnd, Start, End, Constant;

        fun isEnabled() =
            this != None

        fun isStartAndEnd() =
            this == StartAndEnd

        fun isStart() =
            this == Start

        fun isEnd() =
            this == End

        fun isConstant() =
            this == Constant
    }

    private enum class ProgressStage {
        StartPre, StartPost, PreTick, During, EndPre, EndPost, PacketReceiveBreak, TimedOut
    }

    private enum class RenderMode {
        None, Out, In, InOut, OutIn, Static;

        fun isEnabled() =
            this != None
    }

    @Suppress("UNUSED")
    private enum class RenderQueueMode {
        None, Cube, Shape;

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

    private enum class BreakType {
        Primary, Double;

        fun isPrimary() =
            this == Primary
    }

    val renderer = DynamicESP
    private var currentMiningBlock = Array<BreakingContext?>(2) { null }
    private var lastNonEmptyState: BlockState? = null
    private val blockQueue = ArrayDeque<BlockPos>()
    private var queueBreakStartCounter = 0
    private var awaitingQueueBreak = false
    private var returnSlot = -1
    private var swappedSlot = -1
    private var swapped = false
    private var previousSelectedSlot = -1
    private var expectedRotation: RotationContext? = null
    private var rotationPosition: BlockPos? = null
    private var pausedForRotation = false
    private var releaseRotateDelayCounter = 0
    private var reBreakDelayCounter = 0
    private var emptyReBreakDelayCounter = 0
    private var rotated = false
    private var onRotationComplete: Runnable? = null
    private var waitingToReleaseRotation = false
    private var cancelNextSwing = false
    private var breaksPerTickCounter = 0
    private var swingingNextAttack = true
    private var doubleBreakSwapped = false
    private var doubleBreakSwappedCounter = 0
    private var doubleBreakReturnSlot = 0

    init {
        listen<PlayerEvent.Breaking.Update> {
            swingingNextAttack = false
        }

        listen<PlayerEvent.Attack.Block> {
            it.cancel()
            if (swingOnManual) swingMainHand()

            if (swingingNextAttack) {
                cancelNextSwing = true
            } else {
                swingingNextAttack = true
            }

            if (queueBlocks) {
                if (shouldBePlacedInBlockQueue(it.pos)) {
                    if (reverseQueueOrder) {
                        blockQueue.addFirst(it.pos)
                    } else {
                        blockQueue.add(it.pos)
                    }
                    return@listen
                }

                if (blockQueue.contains(it.pos)) return@listen
            }

            currentMiningBlock.forEach { ctx ->
                ctx?.apply {
                    if (it.pos != pos) return@forEach

                    val primary = breakType.isPrimary()

                    if (!primary || (breakState == BreakState.ReBreaking && !reBreak.isStandard())) return@listen

                    if (miningProgress < breakThreshold) return@listen

                    runBetweenHandlers(ProgressStage.EndPre, ProgressStage.EndPost, pos, { lastValidBestTool }) {
                        packetStopBreak(pos)

                        onBlockBreak()
                    }

                    return@listen
                }
            }

            currentMiningBlock[0]?.apply {

                currentMiningBlock[1]?.run { return@apply }

                if (doubleBreak && breakState != BreakState.ReBreaking) {
                    switchCurrentMiningBlockToDouble()
                }
            }

            startBreaking(it.pos)
        }

        listen<PlayerEvent.SwingHand> {
            if (!cancelNextSwing) return@listen

            cancelNextSwing = false
            it.cancel()
        }

        listen<TickEvent.Pre>(1) {
            updateCounters()

            if (shouldWaitForQueuePause()) return@listen

            currentMiningBlock.forEach { ctx ->
                ctx?.apply {

                    mineTicks++

                    val activeState = pos.blockState(world)
                    state = activeState

                    val empty = isStateEmpty(activeState)

                    val bestTool = if (breakType.isPrimary()) {
                        lastNonEmptyState?.run {
                            getBestTool(this, pos)
                        } ?: player.inventory.selectedSlot
                    } else {
                        getBestTool(state, pos)
                    }

                    if (breakType.isPrimary()) {
                        if (!empty) {
                            if (activeState != lastNonEmptyState?.block) {
                                lastNonEmptyState?.apply {
                                    transformProgress(block.hardness / activeState.block.hardness)
                                }
                            }

                            lastNonEmptyState = activeState
                        }

                        lastNonEmptyState?.let {
                            lastValidBestTool = bestTool
                        }

                        runHandlers(ProgressStage.PreTick, pos, lastValidBestTool, empty)

                        if (strict
                            && !swapped
                            && player.inventory.selectedSlot != previousSelectedSlot
                        ) {
                            resetProgress()
                            currentMiningBlock[1]?.apply {
                                resetProgress()
                            }
                        }
                        previousSelectedSlot = player.inventory.selectedSlot
                    }

                    if ((pauseWhileUsingItems && player.isUsingItem) || pausedForRotation) {
                        return@forEach
                    }

                    currentBreakDelta = if (swapMethod.isEnabled()) {
                        if (breakType.isPrimary()) {
                            calcBreakDelta(state, pos, lastValidBestTool)
                        } else {
                            calcBreakDelta(state, pos, bestTool)
                        }
                    } else {
                        calcBreakDelta(state, pos, player.inventory.selectedSlot)
                    }

                    updateBreakDeltas(currentBreakDelta)

                    if (renderMode.isEnabled()) updateRenders()

                    if (breakingAnimation
                        && breakType.isPrimary()
                    )
                        world.setBlockBreakingInfo(
                            player.id,
                            pos,
                            (miningProgress * (2 - breakThreshold) * 10).toInt().coerceAtMost(9)
                        )

                    when (breakState) {
                        BreakState.Breaking -> {
                            val threshold = if (breakType.isPrimary()) breakThreshold else 1f

                            if (miningProgress < threshold) return@forEach

                            timeCompleted = System.currentTimeMillis()

                            if (breakType.isPrimary()) {
                                runBetweenHandlers(
                                    ProgressStage.EndPre,
                                    ProgressStage.EndPost,
                                    pos,
                                    { lastValidBestTool }) {
                                    packetStopBreak(pos)

                                    onBlockBreak(doubleBreakBlock = false)
                                }
                                return@forEach
                            }

                            doubleBreakSwapTo(bestTool)
                            onBlockBreak(doubleBreakBlock = true)
                        }

                        BreakState.ReBreaking -> {
                            if (miningProgress < breakThreshold) {
                                runHandlers(ProgressStage.During, pos, lastValidBestTool, empty = empty)
                                return@forEach
                            }

                            if (isOutOfRange(pos.toCenterPos()) || !reBreak.isEnabled()) {
                                nullifyCurrentBreakingBlock(false)
                                return@forEach
                            }

                            if (breakNextQueueBlock() || reBreak.isStandard()) return@forEach

                            if (empty) {
                                if (emptyReBreakDelayCounter > 0) return@forEach
                                emptyReBreakDelayCounter = emptyReBreakDelay
                            } else {
                                if (reBreakDelayCounter > 0) return@forEach
                                reBreakDelayCounter = reBreakDelay
                            }

                            if (!reBreak.isFastAutomatic() && empty) {
                                return@forEach
                            }

                            runBetweenHandlers(
                                ProgressStage.EndPre,
                                ProgressStage.EndPost,
                                pos,
                                { lastValidBestTool },
                                empty = empty
                            ) {
                                packetStopBreak(pos)

                                onBlockBreak()
                            }
                        }

                        BreakState.AwaitingResponse -> {
                            if (!validateBreak) {
                                if (breakType.isPrimary()) {
                                    runHandlers(ProgressStage.EndPost, pos, lastValidBestTool)
                                }
                                onBlockBreak(doubleBreakBlock = !breakType.isPrimary())
                                return@forEach
                            }

                            if (System.currentTimeMillis() - timeCompleted < timeoutDelay * 1000) return@forEach

                            val primary = breakType.isPrimary()

                            if (!primary) nullifyCurrentBreakingBlock(true)
                            if (breakNextQueueBlock()) return@forEach

                            if (primary) {
                                runHandlers(ProgressStage.TimedOut, pos, lastValidBestTool)
                            }

                            nullifyCurrentBreakingBlock(!breakType.isPrimary())
                        }
                    }
                }
            }
        }

        listen<TickEvent.Post> {
            if (doubleBreakSwapped && doubleBreakSwappedCounter >= 1) {
                returnToOriginalDoubleBreakSlot()
            }
        }

        listen<WorldEvent.BlockChange> {
            currentMiningBlock.forEach { ctx ->
                ctx?.apply {
                    if (it.pos != pos || !isStateBroken(pos.blockState(world), it.newState)) return@forEach

                    if (breakType.isPrimary()) {
                        runHandlers(ProgressStage.PacketReceiveBreak, pos, lastValidBestTool)
                    } else {
                        if (doubleBreakSwapped) {
                            returnToOriginalDoubleBreakSlot()
                        }
                    }

                    onBlockBreak(packetReceiveBreak = true, doubleBreakBlock = !breakType.isPrimary())
                }
            }
        }

        listen<RotationEvent.Update> {
            if (!rotate.isEnabled()) return@listen

            rotationPosition?.let { pos ->
                lastNonEmptyState?.let { state ->
                    val boxList = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
                    val rotationContext =
                        findRotation(boxList, TaskFlowModule.rotation, TaskFlowModule.interact, emptySet()) { true }
                    rotationContext?.let { context ->
                        it.context = context
                        expectedRotation = context
                    }
                }
            } ?: run {
                expectedRotation = null
            }

            if (!rotated || !waitingToReleaseRotation) return@listen

            releaseRotateDelayCounter--

            if (releaseRotateDelayCounter <= 0) {
                waitingToReleaseRotation = false
                rotationPosition = null
                rotated = false
            }
        }

        listen<RotationEvent.Post> {
            if (!rotate.isEnabled()) return@listen

            expectedRotation?.let { expectedRot ->
                rotationPosition?.let { pos ->
                    if (it.context != expectedRot) {
                        pausedForRotation = true
                        return@listen
                    }

                    val boxList = lastNonEmptyState?.getOutlineShape(world, pos)?.boundingBoxes?.map { it.offset(pos) }
                    if (verifyRotation(
                            boxList,
                            RotationManager.currentRotation.vector,
                            RotationManager.currentContext?.hitResult
                        )
                    ) {
                        onRotationComplete?.run()
                        onRotationComplete = null
                        pausedForRotation = false
                    } else {
                        pausedForRotation = true
                    }

                    return@listen
                }
            }

            onRotationComplete = null
            pausedForRotation = false
        }

        listen<RenderEvent.World> {
            renderer.clear()

            currentMiningBlock.forEach { ctx ->
                ctx?.run { buildRenders() }
            }

            if (renderQueueMode.isEnabled()) {
                blockQueue.forEach { pos ->
                    var boxes = if (renderQueueMode == RenderQueueMode.Shape) {
                        pos.blockState(world).getOutlineShape(world, pos).boundingBoxes
                    } else {
                        listOf(Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
                    }
                    boxes = boxes.map {
                        val reSized = lerp(renderQueueSize.toDouble(), Box(it.center, it.center), it); reSized.offset(
                        pos
                    )
                    }

                    val indexFactor = blockQueue.indexOf(pos).toDouble() / blockQueue.size.toDouble()

                    val fillColour = if (queueFillColourMode == ColourMode.Static) {
                        queueStaticFillColour
                    } else {
                        lerp(indexFactor, queueStartFillColour, queueEndFillColour)
                    }

                    val outlineColour = if (queueOutlineColourMode == ColourMode.Static) {
                        queueStaticOutlineColour
                    } else {
                        lerp(indexFactor, queueStartOutlineColour, queueEndOutlineColour)
                    }

                    boxes.forEach { box ->
                        val dynamicAABB = DynamicAABB()
                        dynamicAABB.update(box)

                        if (renderQueueSetting != RenderSetting.Outline) {
                            renderer.buildFilled(dynamicAABB, fillColour)
                        }

                        if (renderQueueSetting != RenderSetting.Fill) {
                            renderer.buildOutline(dynamicAABB, outlineColour)
                        }
                    }
                }
            }

            renderer.upload()
        }

        onDisable {
            currentMiningBlock[0] = null
            currentMiningBlock[1] = null
            lastNonEmptyState = null
            blockQueue.clear()
            queueBreakStartCounter = 0
            awaitingQueueBreak = false
            returnSlot = -1
            swappedSlot = -1
            swapped = false
            previousSelectedSlot = -1
            expectedRotation = null
            rotationPosition = null
            pausedForRotation = false
            releaseRotateDelayCounter = 0
            reBreakDelayCounter = 0
            emptyReBreakDelayCounter = 0
            rotated = false
            onRotationComplete = null
            waitingToReleaseRotation = false
            cancelNextSwing = false
            breaksPerTickCounter = 0
            swingingNextAttack = true
            doubleBreakSwapped = false
            doubleBreakSwappedCounter = 0
            doubleBreakReturnSlot = 0
        }
    }

    private fun SafeContext.startBreaking(pos: BlockPos) {
        val state = pos.blockState(world)
        val bestTool = getBestTool(state, pos)
        if (!isStateEmpty(state)) lastNonEmptyState = state

        val breakDelta = if (swapMethod.isEnabled()) {
            calcBreakDelta(state, pos, bestTool)
        } else {
            calcBreakDelta(state, pos, player.inventory.selectedSlot)
        }
        val instaBreak = breakDelta >= breakThreshold

        previousSelectedSlot = player.inventory.selectedSlot

        runBetweenHandlers(
            ProgressStage.StartPre,
            ProgressStage.StartPost,
            pos,
            { bestTool },
            instaBroken = instaBreak
        ) {
            packetStartBreak(pos)

            currentMiningBlock[0] = BreakingContext(pos, state, BreakState.Breaking, breakDelta, bestTool)

            if (!instaBreak) return@runBetweenHandlers

            packetStopBreak(pos)

            onBlockBreak()
        }
    }

    private fun SafeContext.runBetweenHandlers(
        preStage: ProgressStage,
        postStage: ProgressStage,
        pos: BlockPos,
        bestTool: Supplier<Int>,
        empty: Boolean = false,
        instaBroken: Boolean = false,
        task: Runnable
    ) {
        handleRotations(preStage, pos, empty = empty, instaBroken = instaBroken)

        val postRotationTask = Runnable {
            handleAutoSwap(preStage, bestTool.get(), empty = empty, instaBroken = instaBroken)
            handleSwing(preStage, empty = empty, instaBroken = instaBroken)
            task.run()
            runHandlers(postStage, pos, bestTool.get(), empty = empty, instaBroken = instaBroken)
        }

        if (pausedForRotation) {
            onRotationComplete = postRotationTask
        } else {
            postRotationTask.run()
        }
    }

    private fun SafeContext.runHandlers(
        progressStage: ProgressStage,
        pos: BlockPos,
        bestTool: Int,
        empty: Boolean = false,
        instaBroken: Boolean = false
    ) {
        handleRotations(progressStage, pos, empty = empty, instaBroken = instaBroken)
        handleAutoSwap(progressStage, bestTool, empty = empty, instaBroken = instaBroken)
        handleSwing(progressStage, empty = empty, instaBroken = instaBroken)
    }

    private fun SafeContext.handleRotations(
        progressStage: ProgressStage,
        pos: BlockPos,
        empty: Boolean = false,
        instaBroken: Boolean = false
    ) {
        when (progressStage) {
            ProgressStage.PreTick -> {
                if (rotate.isConstant()
                    && !rotated
                    && !empty
                    && (currentMiningBlock[0]?.breakState != BreakState.ReBreaking
                            || !reBreak.isStandard()
                            )
                ) {
                    rotateTo(pos)
                }
            }

            ProgressStage.StartPre -> if (rotate.isEnabled() && (!rotate.isEnd() || instaBroken)) rotateTo(pos)

            ProgressStage.EndPre -> if (rotate.isEnabled() && !rotate.isStart()) rotateTo(pos)

            ProgressStage.During -> if (rotate.isConstant() && !empty && !reBreak.isStandard()) rotateTo(pos)

            ProgressStage.StartPost -> if ((instaBroken && !validateBreak) || (!instaBroken && (rotate.isStart() || rotate.isStartAndEnd()))) checkReleaseRotation()

            ProgressStage.EndPost -> if (!validateBreak || empty) checkReleaseRotation()

            ProgressStage.PacketReceiveBreak,
            ProgressStage.TimedOut -> checkReleaseRotation()
        }
    }

    private fun SafeContext.handleAutoSwap(
        progressStage: ProgressStage,
        bestTool: Int,
        empty: Boolean = false,
        instaBroken: Boolean = false
    ) {
        if (!swapMethod.isEnabled()) return

        when (progressStage) {
            ProgressStage.PreTick -> {
                if (!swapped) return

                when {
                    player.inventory.selectedSlot != swappedSlot -> resetSwap()

                    swapMode.isConstant() -> {
                        if (swappedSlot != bestTool) swapTo(bestTool)
                    }
                }

                if (swapMode.isStartAndEnd()) returnToOriginalSlot()
            }

            ProgressStage.StartPre -> if (!swapped && (!swapMode.isEnd() || instaBroken)) swapTo(bestTool)

            ProgressStage.EndPre -> if (!swapped && !swapMode.isStart()) swapTo(bestTool)

            ProgressStage.During -> if (swapped && swapMode.isConstant() && swappedSlot != bestTool) swapTo(bestTool)

            ProgressStage.StartPost -> {
                if (!swapped) return

                if ((instaBroken && !validateBreak)
                    || (swapMethod.isSilent() && !swapMode.isConstant())
                ) {
                    returnToOriginalSlot()
                }
            }

            ProgressStage.EndPost -> {
                if (!swapped) return

                if ((!validateBreak || empty)
                    || (swapMethod.isSilent() && !swapMode.isConstant())
                ) {
                    returnToOriginalSlot()
                }
            }

            ProgressStage.PacketReceiveBreak,
            ProgressStage.TimedOut -> returnToOriginalSlot()
        }
    }

    private fun SafeContext.handleSwing(
        progressStage: ProgressStage,
        empty: Boolean = false,
        instaBroken: Boolean = false
    ) {
        if (!swingMode.isEnabled()) return

        when (progressStage) {
            ProgressStage.PreTick -> {
                currentMiningBlock[0]?.apply {
                    if (swingMode.isConstant() && breakState == BreakState.Breaking) {
                        swingMainHand()
                    }
                }
            }

            ProgressStage.During -> if (swingMode.isConstant() && (!empty || reBreak.isFastAutomatic())) swingMainHand()

            ProgressStage.StartPre -> if (!swingMode.isEnd() || instaBroken) swingMainHand()

            ProgressStage.EndPre -> if (!swingMode.isStart()) swingMainHand()

            ProgressStage.EndPost,
            ProgressStage.StartPost,
            ProgressStage.PacketReceiveBreak,
            ProgressStage.TimedOut -> {
            }
        }
    }

    private fun SafeContext.swingMainHand() {
        player.swingHand(Hand.MAIN_HAND, false)
        connection.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
    }

    private fun updateCounters() {
        breaksPerTickCounter = 0

        if (reBreakDelayCounter > 0) {
            reBreakDelayCounter--
        }
        if (emptyReBreakDelayCounter > 0) {
            emptyReBreakDelayCounter--
        }
        if (queueBreakStartCounter > 0) {
            queueBreakStartCounter--
        }
        if (doubleBreakSwapped) {
            doubleBreakSwappedCounter++
        }
    }

    private fun switchCurrentMiningBlockToDouble() {
        currentMiningBlock[1] = currentMiningBlock[0]
        currentMiningBlock[0] = null
        currentMiningBlock[1]?.breakType = BreakType.Double
    }

    private fun SafeContext.rotateTo(pos: BlockPos) {
        waitingToReleaseRotation = false
        releaseRotateDelayCounter = rotateReleaseDelay
        rotationPosition = pos
        rotated = true
        if (!verifyRotation(
                lastNonEmptyState?.getOutlineShape(world, pos)?.boundingBoxes?.map { it.offset(pos) },
                RotationManager.currentRotation.vector,
                RotationManager.currentContext?.hitResult
            )
        ) {
            pausedForRotation = true
        }
    }

    private fun SafeContext.verifyRotation(boxes: Collection<Box>?, lookVec: Vec3d, hitResult: HitResult?): Boolean {
        if (rayCast && (hitResult?.type == HitResult.Type.ENTITY || (hitResult as BlockHitResult?)?.blockPos != rotationPosition)) {
            return false
        }

        boxes?.let {
            return it.any { box ->
                lookIntersectsBox(player.eyePos, lookVec, box)
            }
        } ?: return false
    }

    private fun lookIntersectsBox(start: Vec3d, direction: Vec3d, box: Box): Boolean {
        val invDirX = 1.0 / direction.x
        val invDirY = 1.0 / direction.y
        val invDirZ = 1.0 / direction.z

        val tMinX = if (invDirX >= 0) (box.minX - start.x) * invDirX else (box.maxX - start.x) * invDirX
        val tMaxX = if (invDirX >= 0) (box.maxX - start.x) * invDirX else (box.minX - start.x) * invDirX

        val tMinY = if (invDirY >= 0) (box.minY - start.y) * invDirY else (box.maxY - start.y) * invDirY
        val tMaxY = if (invDirY >= 0) (box.maxY - start.y) * invDirY else (box.minY - start.y) * invDirY

        val tMinZ = if (invDirZ >= 0) (box.minZ - start.z) * invDirZ else (box.maxZ - start.z) * invDirZ
        val tMaxZ = if (invDirZ >= 0) (box.maxZ - start.z) * invDirZ else (box.minZ - start.z) * invDirZ

        val tMin = maxOf(tMinX, tMinY, tMinZ)
        val tMax = minOf(tMaxX, tMaxY, tMaxZ)

        return tMax >= tMin && tMax > 0
    }

    private fun checkReleaseRotation() {
        if (!rotated || waitingToReleaseRotation) return

        if (releaseRotateDelayCounter <= 0) {
            rotationPosition = null
            rotated = false
        } else {
            waitingToReleaseRotation = true
        }
    }

    private fun SafeContext.swapTo(slot: Int) {
        if (swapped && swapMethod.isNCPSilent()) returnToOriginalSlot()

        if (returnSlot == -1) {
            returnSlot = if (!doubleBreakSwapped) {
                player.inventory.selectedSlot
            } else {
                doubleBreakReturnSlot
            }
        }

        if (swapMethod.isSilent()) {
            silentSwapTo(slot, false)
        } else {
            player.inventory.selectedSlot = slot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        }

        swappedSlot = slot
        swapped = true
    }

    private fun SafeContext.silentSwapTo(slot: Int, returningToOriginalSlot: Boolean) {
        if (swapMethod.isStandardSilent()) {
            connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
            return
        }

        val screenHandler = player.playerScreenHandler
        var itemStack = player.mainHandStack
        var newSlot = slot
        var fromSlot = player.inventory.selectedSlot

        if (returningToOriginalSlot) {
            newSlot = swappedSlot
            itemStack = player.inventory.getStack(returnSlot)
            fromSlot = returnSlot
        }

        connection.sendPacket(
            ClickSlotC2SPacket(
                screenHandler.syncId,
                screenHandler.revision,
                newSlot + 36,
                fromSlot,
                SlotActionType.SWAP,
                itemStack,
                Int2ObjectArrayMap()
            )
        )
    }

    private fun SafeContext.returnToOriginalSlot() {
        if (!swapped || returnSlot == -1) return

        if (swapMethod.isSilent()) {
            silentSwapTo(returnSlot, true)
        } else {
            player.inventory.selectedSlot = returnSlot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(returnSlot))
        }

        if (doubleBreakSwapped && (!swapMethod.isSilent() || swapMode.isConstant())) {
            doubleBreakSwapped = false
            doubleBreakReturnSlot = 0
        }

        resetSwap()

        return
    }

    private fun SafeContext.doubleBreakSwapTo(slot: Int) {
        val currentSelectedSlot = player.inventory.selectedSlot

        if (!doubleBreakSwapped) {
            doubleBreakReturnSlot = currentSelectedSlot
        }

        if (slot != currentSelectedSlot) {
            player.inventory.selectedSlot = slot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        }

        doubleBreakSwappedCounter = 0
        doubleBreakSwapped = true
    }

    private fun SafeContext.returnToOriginalDoubleBreakSlot() {
        if (!doubleBreakSwapped) return

        if (doubleBreakReturnSlot != player.inventory.selectedSlot) {
            player.inventory.selectedSlot = doubleBreakReturnSlot
            connection.sendPacket(UpdateSelectedSlotC2SPacket(doubleBreakReturnSlot))
        }

        doubleBreakSwapped = false
    }

    private fun resetSwap() {
        returnSlot = -1
        swappedSlot = -1
        swapped = false
    }

    private fun SafeContext.isOutOfRange(vec: Vec3d) =
        player.eyePos.distanceTo(vec) > range

    private fun SafeContext.onBlockBreak(packetReceiveBreak: Boolean = false, doubleBreakBlock: Boolean = false) {
        val block = if (doubleBreakBlock) 1 else 0

        currentMiningBlock[block]?.apply {
            if (!isOutOfRange(pos.toCenterPos()) || packetReceiveBreak) {
                checkClientSideBreak(packetReceiveBreak, pos, doubleBreakBlock = doubleBreakBlock)
            }

            if (timeCompleted == -1L) {
                timeCompleted = System.currentTimeMillis()
            }

            if (validateBreak && breakState == BreakState.Breaking) {
                breakState = BreakState.AwaitingResponse
                return
            }

            if (!breakType.isPrimary() && (breakState == BreakState.AwaitingResponse || !validateBreak)) {
                nullifyCurrentBreakingBlock(true)
            }

            breaksPerTickCounter++

            queueBreakStartCounter = queueBreakDelay
            if (breakNextQueueBlock(doubleBreakBlock = doubleBreakBlock)) return

            if (reBreak.isEnabled() && !isOutOfRange(pos.toCenterPos()) && breakType.isPrimary()) {
                if (breakState != BreakState.ReBreaking) {
                    breakState = BreakState.ReBreaking
                }

                if (strict) resetProgress()
                return
            }

            nullifyCurrentBreakingBlock(doubleBreakBlock)
        }
    }

    private fun SafeContext.nullifyCurrentBreakingBlock(doubleBreakBlock: Boolean) {
        val block = if (doubleBreakBlock) 1 else 0

        if (!doubleBreakBlock) {
            currentMiningBlock[block]?.apply {
                if (breakingAnimation) world.setBlockBreakingInfo(player.id, pos, -1)
                runHandlers(ProgressStage.TimedOut, pos, lastValidBestTool)
            }
        }

        currentMiningBlock[block] = null
    }

    private fun isStateBroken(previousState: BlockState?, activeState: BlockState): Boolean {
        previousState?.let { previous ->
            if (isStateEmpty(previous)) return false

            return activeState.isAir || (
                    activeState.fluidState.fluid is WaterFluid
                            && !activeState.properties.contains(Properties.WATERLOGGED)
                            && previous.properties.contains(Properties.WATERLOGGED)
                            && previous.get(Properties.WATERLOGGED)
                    )
        } ?: return false
    }

    private fun isStateEmpty(state: BlockState) =
        state.isAir || (
                (!state.properties.contains(Properties.WATERLOGGED)
                        || !state.get(Properties.WATERLOGGED))
                        && !state.fluidState.isEmpty
                )

    private fun SafeContext.checkClientSideBreak(
        packetReceiveBreak: Boolean,
        pos: BlockPos,
        doubleBreakBlock: Boolean = false
    ) {
        if (packetReceiveBreak || (!validateBreak && !doubleBreakBlock)) {
            interaction.breakBlock(pos)
        }
    }

    private fun shouldBePlacedInBlockQueue(pos: BlockPos): Boolean =
        (((currentMiningBlock[0] != null && !doubleBreak) || currentMiningBlock[1] != null) || !blockQueue.isEmpty())
                && (currentMiningBlock[0]?.breakState != BreakState.ReBreaking || !blockQueue.isEmpty())
                && currentMiningBlock[0]?.pos != pos
                && currentMiningBlock[1]?.pos != pos
                && !blockQueue.contains(pos)

    private fun SafeContext.breakNextQueueBlock(doubleBreakBlock: Boolean = false): Boolean {
        if (!queueBlocks || currentMiningBlock.any { it?.timeCompleted == -1L }) return false

        var startedBreakingAtLeastOne = false

        while (true) {
            filterBlockQueueUntilNextPossible()?.let { block ->
                currentMiningBlock[1]?.run {
                    return startedBreakingAtLeastOne
                }

                val requiresAnotherTickDelay = doubleBreakBlock && !validateBreak && queueBreakDelay <= 0 && doubleBreak

                if (queueBreakStartCounter > 0 || breaksPerTickCounter >= 1 || requiresAnotherTickDelay) {
                    if (requiresAnotherTickDelay) queueBreakStartCounter++
                    awaitingQueueBreak = true
                    return true
                }

                currentMiningBlock[0]?.apply {
                    if (timeCompleted != -1L || breakState == BreakState.ReBreaking) return@apply

                    if (!doubleBreak) return startedBreakingAtLeastOne

                    switchCurrentMiningBlockToDouble()
                }

                blockQueue.remove(block)
                startBreaking(block)

                if (!doubleBreak || startedBreakingAtLeastOne) {
                    return true
                }

                startedBreakingAtLeastOne = true
            } ?: return startedBreakingAtLeastOne
        }
    }

    private fun SafeContext.filterBlockQueueUntilNextPossible(): BlockPos? {
        while (true) {
            val block = blockQueue.firstOrNull() ?: return null

            if (isOutOfRange(block.toCenterPos())) {
                blockQueue.remove(block)
                continue
            }

            return block
        }
    }

    private fun SafeContext.shouldWaitForQueuePause(): Boolean {
        if (awaitingQueueBreak) {
            if (queueBreakStartCounter > 0) return true

            awaitingQueueBreak = false
            if (breakNextQueueBlock()) return true
        }

        return false
    }

    private class BreakingContext(
        val pos: BlockPos,
        var state: BlockState,
        var breakState: BreakState,
        var currentBreakDelta: Float,
        var lastValidBestTool: Int,
    ) {
        var mineTicks = 0
        var breakType = BreakType.Primary
        var additiveBreakDelta = currentBreakDelta
        var timeCompleted: Long = -1
        var previousBreakDelta = 0f
        var lastLerpedBoxes: HashSet<Box> = hashSetOf()
        var lastLerpFillColour: Color? = null
        var lastLerpOutlineColour: Color? = null

        var boxList = if (renderMode.isEnabled()) {
            state.getOutlineShape(mc.world, pos).boundingBoxes.toSet()
        } else {
            null
        }

        val miningProgress: Float
            get() = if (breakMode.isTotal()) {
                mineTicks * currentBreakDelta
            } else {
                additiveBreakDelta
            }

        val previousMiningProgress: Float
            get() = if (breakMode.isTotal()) {
                (mineTicks - 1) * previousBreakDelta
            } else {
                additiveBreakDelta - currentBreakDelta
            }

        fun resetProgress() {
            mineTicks = 0
            additiveBreakDelta = 0f
        }

        fun transformProgress(percentage: Float) {
            additiveBreakDelta *= percentage
        }

        fun updateBreakDeltas(newBreakDelta: Float) {
            previousBreakDelta = currentBreakDelta
            currentBreakDelta = newBreakDelta
            additiveBreakDelta += newBreakDelta
        }

        fun updateRenders() {
            boxList = if (breakType.isPrimary()) {
                lastNonEmptyState?.getOutlineShape(mc.world, pos)?.boundingBoxes?.toSet()
            } else {
                state.getOutlineShape(mc.world, pos)?.boundingBoxes?.toSet()
            }
        }

        fun SafeContext.buildRenders() {
            if (!renderIfEmpty && isStateEmpty(state)) return

            val threshold = if (breakType.isPrimary()) {
                2f - breakThreshold
            } else {
                1f
            }
            val previousFactor = previousMiningProgress * threshold
            val nextFactor = miningProgress * threshold
            val currentFactor = lerp(mc.tickDelta, previousFactor, nextFactor)

            val paused = (pauseWhileUsingItems && player.isUsingItem) || pausedForRotation || awaitingQueueBreak

            val fillColour = if (fillColourMode == ColourMode.Dynamic) {
                val lerpColour = lerp(currentFactor.toDouble(), startFillColour, endFillColour)
                if (!paused) {
                    lastLerpFillColour = lerpColour
                    lerpColour
                } else {
                    lastLerpFillColour ?: startFillColour
                }
            } else {
                staticFillColour
            }

            val outlineColour = if (outlineColourMode == ColourMode.Dynamic) {
                val lerpColour = lerp(currentFactor.toDouble(), startOutlineColour, endOutlineColour)
                if (!paused) {
                    lastLerpOutlineColour = lerpColour
                    lerpColour
                } else {
                    lastLerpOutlineColour ?: startOutlineColour
                }
            } else {
                staticOutlineColour
            }

            if (paused) {
                lastLerpedBoxes.forEach { box ->
                    val dynamicAABB = DynamicAABB()
                    dynamicAABB.update(box)

                    if (renderSetting != RenderSetting.Outline) {
                        renderer.buildFilled(dynamicAABB, fillColour)
                    }

                    if (renderSetting != RenderSetting.Fill) {
                        renderer.buildOutline(dynamicAABB, outlineColour)
                    }
                }

                return
            }

            lastLerpedBoxes.clear()

            boxList?.forEach { box ->
                val positionedBox = box.offset(pos)

                val renderBox = if (renderMode == RenderMode.Static) {
                    positionedBox
                } else {
                    getLerpBox(positionedBox, currentFactor)
                }

                lastLerpedBoxes.add(renderBox)

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
                    return lerp(factor.toDouble(), boxCenter, box)
                }

                RenderMode.In -> {
                    return lerp(factor.toDouble(), box, boxCenter)
                }

                RenderMode.InOut -> {
                    return if (factor >= 0.5f) {
                        lerp((factor.toDouble() - 0.5) * 2, boxCenter, box)
                    } else {
                        lerp(factor.toDouble() * 2, box, boxCenter)
                    }
                }

                RenderMode.OutIn -> {
                    return if (factor >= 0.5f) {
                        lerp((factor.toDouble() - 0.5) * 2, box, boxCenter)
                    } else {
                        lerp(factor.toDouble() * 2, boxCenter, box)
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
        if (packets != PacketMode.Vanilla || doubleBreak) {
            abortBreak(pos)
        }
        if (packets == PacketMode.Grim || doubleBreak) {
            stopBreak(pos)
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
