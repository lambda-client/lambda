/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request.breaking

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.events.onDynamicRender
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.ManagerUtils.isPosBlocked
import com.lambda.interaction.request.ManagerUtils.newStage
import com.lambda.interaction.request.ManagerUtils.newTick
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Rebreak
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.RedundantSecondary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Secondary
import com.lambda.interaction.request.breaking.BreakManager.activeInfos
import com.lambda.interaction.request.breaking.BreakManager.activeRequest
import com.lambda.interaction.request.breaking.BreakManager.breakInfos
import com.lambda.interaction.request.breaking.BreakManager.breaks
import com.lambda.interaction.request.breaking.BreakManager.canAccept
import com.lambda.interaction.request.breaking.BreakManager.checkForCancels
import com.lambda.interaction.request.breaking.BreakManager.initNewBreak
import com.lambda.interaction.request.breaking.BreakManager.maxInstantBreaksThisTick
import com.lambda.interaction.request.breaking.BreakManager.processNewBreak
import com.lambda.interaction.request.breaking.BreakManager.processRequest
import com.lambda.interaction.request.breaking.BreakManager.simulateAbandoned
import com.lambda.interaction.request.breaking.BreakManager.updateBreakProgress
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.interaction.request.breaking.BrokenBlockHandler.pendingActions
import com.lambda.interaction.request.breaking.BrokenBlockHandler.setPendingConfigs
import com.lambda.interaction.request.breaking.BrokenBlockHandler.startPending
import com.lambda.interaction.request.breaking.SwapInfo.Companion.getSwapInfo
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.interacting.InteractionManager
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.module.hud.ManagerDebugLoggers.breakManagerLogger
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotBroken
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.extension.partialTicks
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.math.lerp
import com.lambda.util.player.gamemode
import com.lambda.util.player.swingHand
import net.minecraft.block.BlockState
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.BlockView
import kotlin.math.max

object BreakManager : RequestHandler<BreakRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = {
        if (activeInfos.isNotEmpty() || breaks.isNotEmpty())
            BreakManager.logger.newStage(BreakManager.tickStage)
        processRequest(activeRequest)
        simulateAbandoned()
             },
    onClose = { checkForCancels() }
), PositionBlocking, Logger {
    private val breakInfos = arrayOfNulls<BreakInfo>(2)

    private val activeInfos
        get() = breakInfos
            .filterNotNull()
            .filter { it.type != RedundantSecondary }

    private var primaryBreak: BreakInfo?
        get() = breakInfos[0]
        set(value) { breakInfos[0] = value }

    private var secondaryBreak: BreakInfo?
        get() = breakInfos[1]
        set(value) { breakInfos[1] = value }

    private val abandonedBreak
        get() = breakInfos[1].let { secondary ->
            if (secondary?.abandoned == true && secondary.type != RedundantSecondary) secondary
            else null
        }

    val currentStackSelection
        get() = activeInfos
            .lastOrNull {
                it.breakConfig.doubleBreak || it.type == Secondary
            }?.context?.itemSelection
            ?: StackSelection.EVERYTHING.select()

    private val pendingBreakCount get() = activeInfos.count() + pendingActions.size
    override val blockedPositions
        get() = activeInfos.map { it.context.blockPos } + pendingActions.map { it.context.blockPos }

    private var activeRequest: BreakRequest? = null

    private var hotbarRequest: HotbarRequest? = null
    val swapped get() = hotbarRequest?.done != false

    private var rotationRequest: RotationRequest? = null
    private val rotated get() = rotationRequest?.done != false

    private var breakCooldown = 0
    var instantBreaksThisTick = 0
    private var maxInstantBreaksThisTick = 0

    private var breaks = mutableListOf<BreakContext>()

    var lastPosStarted: BlockPos? = null
        set(value) {
            if (value != field) RebreakHandler.clearRebreak()
            field = value
        }

    override val logger = breakManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            if (activeInfos.isEmpty() && breaks.isEmpty()) return@listen
            logger.newTick()
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            if (breakCooldown > 0) {
                breakCooldown--
            }
            activeRequest = null
            breaks = mutableListOf()
            instantBreaksThisTick = 0
        }

        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            if (event.pos == RebreakHandler.rebreak?.context?.blockPos) return@listen

            breakInfos
                .firstOrNull { it?.context?.blockPos == event.pos }
                ?.let { info ->
                    // if not broken
                    if (isNotBroken(info.context.cachedState, event.newState)) {
                        // update the cached state
                        info.context.cachedState = event.newState
                        return@listen
                    }
                    destroyBlock(info)
                    if (info.type == RedundantSecondary) {
                        info.nullify()
                        return@listen
                    }
                    info.request.onStop?.invoke(info.context.blockPos)
                    info.internalOnBreak()
                    if (info.callbacksCompleted)
                        RebreakHandler.offerRebreak(info)
                    else info.startPending()
                    info.nullify()
                }
        }

        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
        listen<EntityEvent.Update>(priority = Int.MIN_VALUE) {
            if (it.entity !is ItemEntity) return@listen

            // ToDo: Proper item drop prediction system
            RebreakHandler.rebreak?.let { reBreak ->
                if (matchesBlockItem(reBreak, it.entity)) return@listen
            }

            breakInfos
                .filterNotNull()
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.internalOnItemDrop(it.entity)
        }

        onDynamicRender { render ->
            val activeStack = breakInfos
                .filterNotNull()
                .firstOrNull()?.swapStack ?: return@onDynamicRender

            breakInfos
                .filterNotNull()
                .forEach { info ->
                    val config = info.breakConfig
                    if (!config.renders) return@onDynamicRender
                    val swapMode = info.breakConfig.swapMode
                    val breakDelta = info.context.cachedState.calcBreakDelta(
                        player,
                        world,
                        info.context.blockPos,
                        info.breakConfig,
                        if (info.type != RedundantSecondary && swapMode.isEnabled() && swapMode != BreakConfig.SwapMode.Start) activeStack else null
                    ).toDouble()
                    val currentDelta = info.breakingTicks * breakDelta

                    val threshold = if (info.type == Primary) info.breakConfig.breakThreshold else 1f
                    val adjustedThreshold = threshold + (breakDelta * config.fudgeFactor)

                    val currentProgress = currentDelta / adjustedThreshold
                    val nextTicksProgress = (currentDelta + breakDelta) / adjustedThreshold
                    val interpolatedProgress = lerp(mc.partialTicks, currentProgress, nextTicksProgress)

                    val fillColor = if (config.dynamicFillColor) lerp(
                        interpolatedProgress,
                        config.startFillColor,
                        config.endFillColor
                    )
                    else config.staticFillColor
                    val outlineColor = if (config.dynamicOutlineColor) lerp(
                        interpolatedProgress,
                        config.startOutlineColor,
                        config.endOutlineColor
                    )
                    else config.staticOutlineColor

                    info.context.cachedState.getOutlineShape(world, info.context.blockPos).boundingBoxes.map {
                        it.offset(info.context.blockPos)
                    }.forEach boxes@ { box ->
                        val dynamicAABB = DynamicAABB()
                        val interpolated = interpolateBox(box, interpolatedProgress, info.breakConfig)
                        dynamicAABB.update(interpolated)
                        if (config.fill) render.filled(dynamicAABB, fillColor)
                        if (config.outline) render.outline(dynamicAABB, outlineColor)
                    }
                }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            primaryBreak = null
            secondaryBreak = null
            breakCooldown = 0
        }

        return "Loaded Break Manager"
    }

    /**
     * Attempts to accept and process the request, if there is not already an [activeRequest].
     * If the request is processed and all breaks completed, the [activeRequest] is cleared.
     *
     * @see processRequest
     */
    override fun SafeContext.handleRequest(request: BreakRequest) {
        if (activeRequest != null || PlaceManager.activeThisTick || InteractionManager.activeThisTick || request.contexts.isEmpty()) return

        activeRequest = request
        processRequest(request)
    }

    /**
     * If the request is fresh, local variables are populated through the [processRequest] method.
     * It then attempts to perform as many breaks within this tick as possible from the [instantBreaks] collection.
     * The [breakInfos] are then updated if the dependencies are present, E.G. if the user has rotations enabled,
     * or the player needs to swap to a different hotbar slot.
     *
     * @see performInstantBreaks
     * @see processNewBreak
     * @see updateBreakProgress
     */
    private fun SafeContext.processRequest(breakRequest: BreakRequest?) {
        breakRequest?.let { request ->
            logger.debug("Processing request", breakRequest)
            if (request.fresh) populateFrom(request)
        }

        var noNew = false
        var noProgression = false

        while (true) {
            noNew = breakRequest?.let { request ->
                !processNewBreak(request)
            } != false

            // Reversed so that the breaking order feels natural to the user as the primary break is always the
            // last break to be started
            handlePreProcessing()
            noProgression =
                activeInfos
                    .filter { it.updatedThisTick && it.shouldProgress }
                    .asReversed()
                    .run {
                        if (isEmpty()) true
                        else {
                            forEach { updateBreakProgress(it) }
                            false
                        }
                    }

            if (noNew && noProgression) break
        }

        if (breaks.isEmpty()) {
            if (activeRequest != null) logger.debug("Clearing active request", activeRequest)
            activeRequest = null
        }
        if (instantBreaksThisTick > 0 || activeInfos.isNotEmpty()) {
            activeThisTick = true
        }
    }

    /**
     * Filters the requests [BreakContext]s, and iterates over the [breakInfos] collection looking for matches
     * in positions. If a match is found, the [BreakInfo] is updated with the new context. Otherwise, the break is cancelled.
     * The [instantBreaks] and [breaks] collections are then populated with the new appropriate contexts, and the [maxInstantBreaksThisTick]
     * value is set.
     *
     * @see canAccept
     */
    private fun SafeContext.populateFrom(request: BreakRequest) {
        logger.debug("Populating from request", request)

        // Sanitize the new breaks
        val newBreaks = request.contexts
            .distinctBy { it.blockPos }
            .toMutableList()

        // Update the current break infos
        breakInfos
            .filterNotNull()
            .forEach { info ->
                newBreaks.find { ctx -> ctx.blockPos == info.context.blockPos && canAccept(ctx) }?.let { ctx ->
                    if ((!info.updatedThisTick || info.type == RedundantSecondary) || info.abandoned) {
                        logger.debug("Updating info", info, ctx)
                        if (info.type == RedundantSecondary)
                            info.request.onStart?.invoke(info.context.blockPos)
                        else if (info.abandoned) {
                            info.abandoned = false
                            info.request.onStart?.invoke(info.context.blockPos)
                        } else
                            info.request.onUpdate?.invoke(info.context.blockPos)

                        info.updateInfo(ctx, request)
                    }
                    newBreaks.remove(ctx)
                    return@forEach
                }
            }

        breaks = newBreaks
            .sortedByDescending { it.instantBreak }
            .toMutableList()

        logger.debug("${breaks.size} unprocessed breaks")

        val breakConfig = request.config
        val pendingLimit = (breakConfig.maxPendingBreaks - pendingBreakCount).coerceAtLeast(0)
        maxInstantBreaksThisTick = breakConfig.breaksPerTick.coerceAtMost(pendingLimit)
    }

    /**
     * @return if the break context can be accepted.
     */
    private fun SafeContext.canAccept(newCtx: BreakContext): Boolean {
        if (activeInfos.none { it.context.blockPos == newCtx.blockPos } && isPosBlocked(newCtx.blockPos)) return false

        if (newCtx.instantBreak && instantBreaksThisTick > maxInstantBreaksThisTick) return false

        if (!currentStackSelection.filterStack(player.inventory.getStack(newCtx.hotbarIndex)))
            return false

        val blockState = blockState(newCtx.blockPos)
        val hardness = newCtx.cachedState.getHardness(world, newCtx.blockPos)

        return blockState.isNotEmpty && hardness != 600f && hardness != -1f
    }

    private fun SafeContext.handlePreProcessing() {
        activeInfos
            .filter { it.updatedThisTick }
            .let { infos ->
                rotationRequest = infos.firstOrNull { info -> info.breakConfig.rotateForBreak }
                    ?.let { info ->
                        val rotation = info.context.rotation
                        logger.debug("Requesting rotation", rotation)
                        rotation.submit(false)
                    }

                if (activeInfos.isEmpty()) return

                infos.forEach { it.updatePreProcessing(player, world) }

                infos.firstOrNull()?.let { info ->
                    infos.lastOrNull { it.swapInfo.swap && it.shouldProgress }?.let { last ->
                        val minSwapTicks = max(info.swapInfo.minKeepTicks, last.swapInfo.minKeepTicks)
                        hotbarRequest = with(info) {
                            HotbarRequest(
                                context.hotbarIndex,
                                request.hotbar,
                                request.hotbar.keepTicks.coerceAtLeast(minSwapTicks),
                                request.config.serverSwapTicks - 1
                            ).submit(false)
                        }
                        logger.debug("Submitted hotbar request", hotbarRequest)
                        return
                    }
                }
            }

        hotbarRequest = null

        return
    }

    /**
     * Attempts to start breaking as many [BreakContext]'s from the [breaks] collection as possible.
     *
     * @return false if a context cannot be started or the maximum active breaks has been reached.
     *
     * @see initNewBreak
     */
    private fun SafeContext.processNewBreak(request: BreakRequest): Boolean {
        breaks.forEach { ctx ->
            if (!canAccept(ctx)) return@forEach

            initNewBreak(ctx, request) ?: return false
            breaks.remove(ctx)
            return true
        }
        return false
    }

    /**
     * Attempts to accept the [requestCtx] into the [breakInfos].
     *
     * @return the [BreakInfo] or null if the break context wasn't accepted.
     */
    private fun SafeContext.initNewBreak(
        requestCtx: BreakContext,
        request: BreakRequest
    ): BreakInfo? {
        if (breakCooldown > 0) return null

        val breakInfo = BreakInfo(requestCtx, Primary, request)
        primaryBreak?.let { primaryInfo ->
            if (tickStage !in primaryInfo.breakConfig.breakStageMask) return null

            if (!primaryInfo.breakConfig.doubleBreak || secondaryBreak != null) {
                if (!primaryInfo.updatedThisTick) {
                    primaryInfo.cancelBreak()
                    return@let
                } else return null
            }

            if (!primaryInfo.breaking) return null

            logger.debug("Transforming primary to secondary", primaryInfo)
            secondaryBreak = primaryInfo.apply { type = Secondary }
            secondaryBreak?.stopBreakPacket(world, interaction)
            return@let
        }

        primaryBreak = breakInfo
        setPendingConfigs(request.build)
        logger.success("Initialized break info", breakInfo)
        return primaryBreak
    }

    private fun SafeContext.simulateAbandoned() {
        // Cancelled but double breaking so requires break manager to continue the simulation
        abandonedBreak?.let { abandonedInfo ->
            with (abandonedInfo.request) {
                abandonedInfo.context.blockPos
                    .toStructure(TargetState.Empty)
                    .toBlueprint()
                    .simulate(player.eyePos, interact, rotation, inventory, build)
                    .asSequence()
                    .filterIsInstance<BreakResult.Break>()
                    .filter { canAccept(it.context) }
                    .sorted()
                    .let { sim ->
                        abandonedInfo.updateInfo(sim.firstOrNull()?.context ?: return)
                    }
            }
        }
    }

    private fun checkForCancels() {
        breakInfos
            .filterNotNull()
            .asSequence()
            .filter { !it.updatedThisTick && tickStage in it.breakConfig.breakStageMask }
            .forEach { info ->
                if (info.type == RedundantSecondary && !info.progressedThisTick) {
                    val cachedState = info.context.cachedState
                    if (cachedState.isEmpty) {
                        info.nullify()
                        return@forEach
                    }
                    info.progressedThisTick = true
                    info.breakingTicks++
                } else info.cancelBreak()
            }
    }

    /**
     * Begins the post-break logic sequence for the given [info].
     *
     * [BreakConfirmationMode.None] Will assume the block has been broken server side, and will only persist
     * the [info] if the requester has any untriggered callbacks. E.g., if the block has broken, but the item hasn't dropped
     * and the requester has specified an itemDrop callback.
     *
     * [BreakConfirmationMode.BreakThenAwait] Will perform all post-block break actions, such as spawning break particles,
     * playing sounds, etc. However, it will store the [info] in the pending interaction collections before triggering the
     * [BreakInfo.internalOnBreak] callback, in case the server rejects the break.
     *
     * [BreakConfirmationMode.AwaitThenBreak] Will immediately place the [info] into the pending interaction collections.
     * Once the server responds, confirming the break, the post-break actions will take place, and the [BreakInfo.internalOnBreak]
     * callback will be triggered.
     *
     * @see destroyBlock
     * @see startPending
     */
    private fun SafeContext.onBlockBreak(info: BreakInfo) {
        info.request.onStop?.invoke(info.context.blockPos)
        when (info.breakConfig.breakConfirmation) {
            BreakConfirmationMode.None -> {
                destroyBlock(info)
                info.internalOnBreak()
                if (!info.callbacksCompleted) {
                    info.startPending()
                } else {
                    RebreakHandler.offerRebreak(info)
                }
            }
            BreakConfirmationMode.BreakThenAwait -> {
                destroyBlock(info)
                info.startPending()
            }
            BreakConfirmationMode.AwaitThenBreak -> {
                info.startPending()
            }
        }
        instantBreaksThisTick++
        info.nullify()
    }

    private fun BreakInfo.updatePreProcessing(player: ClientPlayerEntity, world: BlockView) {
        logger.debug("Updating pre-processing", this)

        shouldProgress = !progressedThisTick
                && tickStage in breakConfig.breakStageMask
                && (rotated || type != Primary)

        if (updatedPreProcessingThisTick) return
        updatedPreProcessingThisTick = true

        swapStack = player.inventory.getStack(context.hotbarIndex)
        rebreakPotential = RebreakHandler.getRebreakPotential(this, player, world)
        swapInfo = getSwapInfo(this, player, world)
    }

    /**
     * Attempts to cancel the break.
     *
     * Secondary blocks are monitored by the server, and keep breaking regardless of the clients actions.
     * This means that the break cannot be completely stopped, instead, it must be monitored as we can't start
     * more secondary break infos until the previous has broken or its state has turned to air.
     *
     * If the user has [BreakConfig.unsafeCancels] enabled, the info is made redundant, and mostly ignored.
     * If not, the break continues.
     */
    private fun BreakInfo.cancelBreak() =
        runSafe {
            if (type == RedundantSecondary || abandoned) return@runSafe
            when (type) {
                Primary -> {
                    logger.warning("Cancelling break", this@cancelBreak)
                    nullify()
                    setBreakingTextureStage(player, world, -1)
                    abortBreakPacket(world, interaction)
                    request.onCancel?.invoke(context.blockPos)
                }
                Secondary -> {
                    if (breakConfig.unsafeCancels) {
                        logger.warning("Making break redundant", this@cancelBreak)
                        type = RedundantSecondary
                        setBreakingTextureStage(player, world, -1)
                        request.onCancel?.invoke(context.blockPos)
                    } else {
                        logger.warning("Abandoning break", this@cancelBreak)
                        abandoned = true
                    }
                }
                else -> {}
            }
        }

    /**
     * Nullifies the break. If the block is not broken, the [BreakInfo.internalOnCancel] callback gets triggered
     */
    private fun BreakInfo.nullify() =
        when (type) {
            Primary, Rebreak -> primaryBreak = null
            else -> secondaryBreak = null
        }

    /**
     * A modified version of the vanilla updateBlockBreakingProgress method.
     *
     * @return if the update was successful.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.updateBlockBreakingProgress
     */
    private fun SafeContext.updateBreakProgress(info: BreakInfo): Boolean {
        val config = info.breakConfig
        val ctx = info.context

        info.progressedThisTick = true

        if (!info.breaking) {
            if (!startBreaking(info)) {
                info.nullify()
                info.request.onCancel?.invoke(ctx.blockPos)
                return false
            }
            val swing = config.swing
            if (swing.isEnabled() && (swing != BreakConfig.SwingMode.End || info.type == Rebreak)) {
                swingHand(config.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        if (config.swapMode == BreakConfig.SwapMode.Constant && !swapped) return true

        val hitResult = ctx.result

        if (gamemode.isCreative && world.worldBorder.contains(ctx.blockPos)) {
            breakCooldown = config.breakDelay
            lastPosStarted = ctx.blockPos
            onBlockBreak(info)
            info.startBreakPacket(world, interaction)
            if (config.swing.isEnabled()) {
                swingHand(config.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        val blockState = blockState(ctx.blockPos)
        if (blockState.isEmpty) {
            info.nullify()
            info.request.onCancel?.invoke(ctx.blockPos)
            logger.warning("Block state was unexpectedly empty", info)
            return false
        }

        info.breakingTicks++
        val breakDelta = blockState.calcBreakDelta(player, world, ctx.blockPos, config)
        val progress = breakDelta * (info.breakingTicks - config.fudgeFactor)
        logger.debug("${info.type} progress: $progress, breaking ticks: ${info.breakingTicks}", info)

        if (config.sounds) {
            if (info.soundsCooldown % 4.0f == 0.0f) {
                val blockSoundGroup = blockState.soundGroup
                mc.soundManager.play(
                    PositionedSoundInstance(
                        blockSoundGroup.hitSound,
                        SoundCategory.BLOCKS,
                        (blockSoundGroup.getVolume() + 1.0f) / 8.0f,
                        blockSoundGroup.getPitch() * 0.5f,
                        SoundInstance.createRandom(),
                        ctx.blockPos
                    )
                )
            }
            info.soundsCooldown++
        }

        if (config.particles) {
            mc.particleManager.addBlockBreakingParticles(ctx.blockPos, hitResult.side)
        }

        if (config.breakingTexture) {
            info.setBreakingTextureStage(player, world)
        }

        val swing = config.swing
        if (progress >= info.getBreakThreshold() && swapped) {
            logger.success("Breaking", info)
            if (info.type == Primary) {
                onBlockBreak(info)
                info.stopBreakPacket(world, interaction)
            } else {
                onBlockBreak(info)
            }
            if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) swingHand(config.swingType, Hand.MAIN_HAND)
            breakCooldown = config.breakDelay
        } else {
            if (swing == BreakConfig.SwingMode.Constant) swingHand(config.swingType, Hand.MAIN_HAND)
        }

        return true
    }

    /**
     * A modified version of the minecraft attackBlock method.
     *
     * @return if the block started breaking successfully.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.attackBlock
     */
    private fun SafeContext.startBreaking(info: BreakInfo): Boolean {
        val ctx = info.context

        if (info.rebreakPotential.isPossible()) {
            logger.debug("Handling potential rebreak")
            when (val rebreakResult = RebreakHandler.handleUpdate(info.context, info.request)) {
                is RebreakResult.StillBreaking -> {
                    logger.debug("Rebreak not complete", info)
                    primaryBreak = rebreakResult.breakInfo.apply {
                        type = Primary
                        RebreakHandler.clearRebreak()
                        request.onStart?.invoke(ctx.blockPos)
                    }

                    primaryBreak?.let { primary ->
                        handlePreProcessing()
                        updateBreakProgress(primary)
                    }
                    return true
                }
                is RebreakResult.Rebroke -> {
                    logger.debug("Rebroke", info)
                    info.type = Rebreak
                    info.nullify()
                    info.request.onReBreak?.invoke(ctx.blockPos)
                    return true
                }
                else -> {}
            }
        }

        if (player.isBlockBreakingRestricted(world, ctx.blockPos, gamemode)) return false
        if (!world.worldBorder.contains(ctx.blockPos)) return false

        if (gamemode.isCreative) {
            lastPosStarted = ctx.blockPos
            info.request.onStart?.invoke(ctx.blockPos)
            onBlockBreak(info)
            info.startBreakPacket(world, interaction)
            breakCooldown = info.breakConfig.breakDelay
            return true
        }
        if (info.breaking) return false
        info.request.onStart?.invoke(ctx.blockPos)

        lastPosStarted = ctx.blockPos

        val blockState = blockState(ctx.blockPos)
        if (info.breakingTicks == 0) {
            blockState.onBlockBreakStart(world, ctx.blockPos, player)
        }

        val progress = blockState.calcBreakDelta(player, world, ctx.blockPos, info.breakConfig)

        if (!swapped) return true

        val instantBreakable = progress >= info.getBreakThreshold() && swapped
        if (instantBreakable) {
            logger.success("Instant breaking", info)
            info.vanillaInstantBreakable = progress >= 1 && swapped
            onBlockBreak(info)
            if (!info.vanillaInstantBreakable) breakCooldown = info.breakConfig.breakDelay
            instantBreaksThisTick++
        } else {
            logger.debug("Starting break", info)
            info.apply {
                breaking = true
                breakingTicks = 1
                soundsCooldown = 0.0f
                if (breakConfig.breakingTexture) {
                    setBreakingTextureStage(player, world)
                }
            }
        }

        if (info.breakConfig.breakMode == BreakMode.Packet) {
            info.stopBreakPacket(world, interaction)
        }

        info.startBreakPacket(world, interaction)

        if (info.type == Secondary || (instantBreakable && !info.vanillaInstantBreakable)) {
            info.stopBreakPacket(world, interaction)
        }

        return true
    }

    fun BlockState.calcBreakDelta(
        player: ClientPlayerEntity,
        world: BlockView,
        pos: BlockPos,
        config: BreakConfig,
        item: ItemStack? = null
    ) = runSafe {
        val delta = calcItemBlockBreakingDelta(player, world, pos, item ?: player.mainHandStack)
        //ToDo: This setting requires some fixes / improvements in the player movement prediction to work properly. Currently, it's broken
//        if (config.desyncFix) {
//            val nextTickPrediction = buildPlayerPrediction().next()
//            if (player.isOnGround && !nextTickPrediction.onGround) {
//                delta /= 5.0f
//            }
//
//            val affectedThisTick = player.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)
//            val simulatedPlayer = nextTickPrediction.predictionEntity.player
//            val affectedNextTick = simulatedPlayer.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(simulatedPlayer)
//            if (!affectedThisTick && affectedNextTick) {
//                delta /= 5.0f
//            }
//        }
        delta
    } ?: 0f

    /**
     * @return if the [ItemEntity] matches the [BreakInfo]'s expected item drop.
     */
    fun matchesBlockItem(info: BreakInfo, entity: ItemEntity): Boolean {
        val inRange = info.context.blockPos.toCenterPos().isInRange(entity.pos, 0.5)
        val correctMaterial = info.context.cachedState.block == entity.stack.item.block
        return inRange && correctMaterial
    }

    private fun interpolateBox(box: Box, progress: Double, config: BreakConfig): Box {
        val boxCenter = Box(box.center, box.center)
        return when (config.animation) {
            BreakConfig.AnimationMode.Out -> lerp(progress, boxCenter, box)
            BreakConfig.AnimationMode.In -> lerp(progress, box, boxCenter)
            BreakConfig.AnimationMode.InOut ->
                if (progress >= 0.5f) lerp((progress - 0.5) * 2, boxCenter, box)
                else lerp(progress * 2, box, boxCenter)
            BreakConfig.AnimationMode.OutIn ->
                if (progress >= 0.5f) lerp((progress - 0.5) * 2, box, boxCenter)
                else lerp(progress * 2, boxCenter, box)
            else -> box
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Break.post()
}
