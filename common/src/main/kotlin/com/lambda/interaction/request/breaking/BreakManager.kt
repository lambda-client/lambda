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
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.request.ManagerUtils.isPosBlocked
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.breaking.BreakManager.activeRequest
import com.lambda.interaction.request.breaking.BreakManager.breakInfos
import com.lambda.interaction.request.breaking.BreakManager.breaks
import com.lambda.interaction.request.breaking.BreakManager.canAccept
import com.lambda.interaction.request.breaking.BreakManager.checkForCancels
import com.lambda.interaction.request.breaking.BreakManager.initNewBreak
import com.lambda.interaction.request.breaking.BreakManager.instantBreaks
import com.lambda.interaction.request.breaking.BreakManager.maxBreaksThisTick
import com.lambda.interaction.request.breaking.BreakManager.performInstantBreaks
import com.lambda.interaction.request.breaking.BreakManager.processNewBreaks
import com.lambda.interaction.request.breaking.BreakManager.processRequest
import com.lambda.interaction.request.breaking.BreakManager.simulateAbandoned
import com.lambda.interaction.request.breaking.BreakManager.updateBreakProgress
import com.lambda.interaction.request.breaking.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakType.ReBreak
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.interaction.request.breaking.BrokenBlockHandler.pendingActions
import com.lambda.interaction.request.breaking.BrokenBlockHandler.setPendingConfigs
import com.lambda.interaction.request.breaking.BrokenBlockHandler.startPending
import com.lambda.interaction.request.interacting.InteractionManager
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.rotating.RotationRequest
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

object BreakManager : RequestHandler<BreakRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { processRequest(activeRequest); simulateAbandoned() },
    onClose = { checkForCancels() }
), PositionBlocking {
    private var primaryBreak: BreakInfo?
        get() = breakInfos[0]
        set(value) { breakInfos[0] = value }
    private var secondaryBreak: BreakInfo?
        get() = breakInfos[1]
        set(value) { breakInfos[1] = value }
    private val breakInfos = arrayOfNulls<BreakInfo>(2)
    val currentStackSelection
        get() = breakInfos
            .lastOrNull {
                it?.isRedundant == false && (it.breakConfig.doubleBreak || it.isSecondary)
            }?.context?.itemSelection
            ?: StackSelection.EVERYTHING.select()

    private val pendingBreakCount get() = breakInfos.count { it != null } + pendingActions.size
    override val blockedPositions
        get() = breakInfos.mapNotNull { it?.context?.blockPos } + pendingActions.map { it.context.blockPos }

    private var activeRequest: BreakRequest? = null

    private var rotationRequest: RotationRequest? = null
    private val rotated get() = rotationRequest?.done != false

    private var breakCooldown = 0
    var breaksThisTick = 0
    private var maxBreaksThisTick = 0

    private var breaks = mutableListOf<BreakContext>()
    private var instantBreaks = mutableListOf<BreakContext>()

    var lastPosStarted: BlockPos? = null
        set(value) {
            if (value != field) ReBreakManager.clearReBreak()
            field = value
        }

    fun Any.onBreak(
        alwaysListen: Boolean = false,
        priority: Int = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Break>(priority, alwaysListen) {
        block()
    }

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            if (breakCooldown > 0) {
                breakCooldown--
            }
            breakInfos.forEach { info ->
                info?.tickStats()
            }
            activeRequest = null
            breaks = mutableListOf()
            instantBreaks = mutableListOf()
            breaksThisTick = 0
        }

        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            if (event.pos == ReBreakManager.reBreak?.context?.blockPos) return@listen

            breakInfos
                .filterNotNull()
                .firstOrNull { it.context.blockPos == event.pos }
                ?.let { info ->
                    val currentState = info.context.cachedState
                    // if not broken
                    if (isNotBroken(currentState, event.newState)) {
                        // update the cached state
                        info.context.cachedState = event.newState
                        return@listen
                    }
                    destroyBlock(info)
                    if (info.isRedundant) {
                        info.nullify()
                        return@listen
                    }
                    info.request.onStop?.invoke(info.context.blockPos)
                    info.internalOnBreak()
                    if (info.callbacksCompleted)
                        ReBreakManager.offerReBreak(info)
                    else info.startPending()
                    info.nullify()
                }
        }

        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
        listen<EntityEvent.Update>(priority = Int.MIN_VALUE) {
            if (it.entity !is ItemEntity) return@listen

            // ToDo: Proper item drop prediction system
            ReBreakManager.reBreak?.let { reBreak ->
                if (matchesBlockItem(reBreak, it.entity)) return@listen
            }

            breakInfos
                .filterNotNull()
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.internalOnItemDrop(it.entity)
        }

        listen<RenderEvent.StaticESP> { event ->
            val activeStack = breakInfos
                .filterNotNull()
                .firstOrNull()?.let { info ->
                    player.inventory.getStack(info.context.hotbarIndex)
                } ?: return@listen

            breakInfos
                .filterNotNull()
                .forEach { info ->
                    val config = info.breakConfig
                    if (!config.renders) return@listen
                    val swapMode = info.breakConfig.swapMode
                    val breakDelta = info.context.cachedState.calcBreakDelta(
                        player,
                        world,
                        info.context.blockPos,
                        info.breakConfig,
                        if (!info.isRedundant && swapMode.isEnabled() && swapMode != BreakConfig.SwapMode.Start) activeStack else null
                    ).toDouble()
                    val currentDelta = info.breakingTicks * breakDelta

                    val threshold = if (info.isPrimary) info.breakConfig.breakThreshold else 1f
                    val adjustedThreshold = threshold + (breakDelta * config.fudgeFactor)

                    val currentProgress = currentDelta / adjustedThreshold
                    val nextTicksProgress = (currentDelta + breakDelta) / adjustedThreshold
                    val interpolatedProgress = lerp(mc.partialTicks, currentProgress, nextTicksProgress)

                    val fillColor = if (config.dynamicFillColor) lerp(interpolatedProgress, config.startFillColor, config.endFillColor)
                    else config.staticFillColor
                    val outlineColor = if (config.dynamicOutlineColor) lerp(interpolatedProgress, config.startOutlineColor, config.endOutlineColor)
                    else config.staticOutlineColor

                    info.context.cachedState.getOutlineShape(world, info.context.blockPos).boundingBoxes.map {
                        it.offset(info.context.blockPos)
                    }.forEach boxes@ { box ->
                        val interpolated = interpolateBox(box, interpolatedProgress, info.breakConfig)
                        if (config.fill) event.renderer.buildFilled(interpolated, fillColor)
                        if (config.outline) event.renderer.buildOutline(interpolated, outlineColor)
                    }
                }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            breakInfos.forEach { it?.nullify() }
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
     * @see processNewBreaks
     * @see updateBreakProgress
     */
    private fun SafeContext.processRequest(breakRequest: BreakRequest?) {
        repeat(2) {
            breakRequest?.let { request ->
                if (request.fresh) populateFrom(request)

                if (performInstantBreaks(request)) {
                    processNewBreaks(request)
                }
            }

            // Reversed so that the breaking order feels natural to the user as the primary break is always the
            // last break to be started
            run {
                breakInfos
                    .filterNotNull()
                    .filter { !it.isRedundant && it.updatedThisTick }
                    .also {
                        rotationRequest = it.firstOrNull { info -> info.breakConfig.rotateForBreak }
                            ?.let { info ->
                                val rotation = info.context.rotation
                                if (instantBreaks.isEmpty()) rotation.submit(false) else rotation
                            }
                    }
                    .also  {
                        it.forEach { it.couldReBreak.update() }
                    }
                    .also {
                        if (breakInfos.none { it?.shouldSwap(player, world) == true }) return@also

                        it.firstOrNull()?.let { info ->
                            secondaryBreak?.let { secondary ->
                                val breakDelta = secondary.context.cachedState.calcBreakDelta(
                                    player,
                                    world,
                                    secondary.context.blockPos,
                                    secondary.breakConfig,
                                    player.inventory.getStack(secondary.context.hotbarIndex)
                                )
                                val breakAmount = breakDelta * ((secondary.breakingTicks - secondary.breakConfig.fudgeFactor) + 1)
                                val minKeepTicks = if (breakAmount >= 1.0f) 1 else 0
                                if (!info.context.requestSwap(info.request, minKeepTicks)) {
                                    secondary.serverBreakTicks = 0
                                    return@run
                                }
                                if (minKeepTicks > 0) {
                                    secondary.serverBreakTicks++
                                }
                                return@also
                            }
                            if (!info.context.requestSwap(info.request)) return@run
                        }
                    }
                    .asReversed()
                    .forEach { info ->
                        if (info.progressedThisTick) return@forEach
                        if (tickStage !in info.breakConfig.breakStageMask) return@forEach
                        if (!rotated && info.isPrimary) return@run

                        updateBreakProgress(info)
                    }
            }
        }

        if (instantBreaks.isEmpty() && breaks.isEmpty()) {
            activeRequest = null
        }
        if (breaksThisTick > 0 || breakInfos.any { it != null && !it.isRedundant }) {
            activeThisTick = true
        }
    }

    private fun SafeContext.simulateAbandoned() {
        // Cancelled but double breaking so requires break manager to continue the simulation
        breakInfos
            .asSequence()
            .filterNotNull()
            .filter { it.abandoned && !it.isRedundant }
            .forEach { info ->
                with (info.request) {
                    info.context.blockPos
                        .toStructure(TargetState.Empty)
                        .toBlueprint()
                        .simulate(player.eyePos, interact, rotation, inventory, build)
                        .asSequence()
                        .filterIsInstance<BreakResult.Break>()
                        .filter { canAccept(it.context) }
                        .sorted()
                        .let { sim ->
                            info.updateInfo(sim.firstOrNull()?.context ?: return@forEach)
                        }
                }
            }
    }

    private fun SafeContext.checkForCancels() {
        breakInfos
            .filterNotNull()
            .asSequence()
            .filter { !it.updatedThisTick && tickStage in it.breakConfig.breakStageMask }
            .forEach { info ->
                if (info.isRedundant && !info.progressedThisTick) {
                    val cachedState = info.context.cachedState
                    if (cachedState.isEmpty || cachedState.isAir) {
                        info.nullify()
                        return@forEach
                    }
                    info.progressedThisTick = true
                    info.breakingTicks++
                }
                else info.cancelBreak()
            }
    }

    /**
     * Filters the requests [BreakContext]s, and iterates over the [breakInfos] collection looking for matches
     * in positions. If a match is found, the [BreakInfo] is updated with the new context. Otherwise, the break is cancelled.
     * The [instantBreaks] and [breaks] collections are then populated with the new appropriate contexts, and the [maxBreaksThisTick]
     * value is set.
     *
     * @see canAccept
     */
    private fun SafeContext.populateFrom(request: BreakRequest) {
        // Sanitize the new breaks
        val newBreaks = request.contexts
            .distinctBy { it.blockPos }
            .toMutableList()

        // Update the current break infos
        breakInfos
            .filterNotNull()
            .forEach { info ->
                newBreaks.find { ctx -> ctx.blockPos == info.context.blockPos && canAccept(ctx) }?.let { ctx ->
                    if (!info.updatedThisTick || info.abandoned) {
                        info.updateInfo(ctx, request)
                        if (info.isRedundant)
                            info.request.onStart?.invoke(info.context.blockPos)
                        else if (info.abandoned) {
                            info.abandoned = false
                            info.request.onStart?.invoke(info.context.blockPos)
                        } else
                            info.request.onUpdate?.invoke(info.context.blockPos)
                    }
                    newBreaks.remove(ctx)
                    return@forEach
                }
            }

        instantBreaks = newBreaks
            .filter { it.instantBreak }
            .toMutableList()

        breaks = newBreaks
            .filter { !it.instantBreak }
            .toMutableList()

        val breakConfig = request.config
        val pendingLimit = (breakConfig.maxPendingBreaks - pendingBreakCount).coerceAtLeast(0)
        maxBreaksThisTick = breakConfig.breaksPerTick.coerceAtMost(pendingLimit)
    }

    /**
     * @return if the break context can be accepted.
     */
    private fun SafeContext.canAccept(newCtx: BreakContext): Boolean {
        if (breakInfos.none { it?.context?.blockPos == newCtx.blockPos } && isPosBlocked(newCtx.blockPos)) return false

        if (!currentStackSelection.filterStack(player.inventory.getStack(newCtx.hotbarIndex)))
            return false

        val blockState = blockState(newCtx.blockPos)
        val hardness = newCtx.cachedState.getHardness(world, newCtx.blockPos)

        return blockState.isNotEmpty && hardness != 600f && hardness != -1f
    }

    /**
     * Attempts to break as many [BreakContext]'s as possible from the [instantBreaks] collection within this tick.
     *
     * @return false if a break could not be performed.
     */
    private fun SafeContext.performInstantBreaks(request: BreakRequest): Boolean {
        val iterator = instantBreaks.iterator()
        while (iterator.hasNext()) {
            if (breaksThisTick + 1 > maxBreaksThisTick) return false

            val ctx = iterator.next()

            if (!canAccept(ctx)) continue

            if (request.build.breaking.swapMode.isEnabled() && !ctx.requestSwap(request)) return false
            rotationRequest = if (request.config.rotateForBreak) ctx.rotation.submit(false) else null
            if (!rotated || tickStage !in request.config.breakStageMask) return false

            val breakInfo = initNewBreak(ctx, request) ?: return false
            updateBreakProgress(breakInfo)
            iterator.remove()
        }
        return true
    }

    /**
     * Attempts to start breaking as many [BreakContext]'s from the [breaks] collection as possible.
     *
     * @return false if a context cannot be started or the maximum active breaks has been reached.
     *
     * @see initNewBreak
     */
    private fun SafeContext.processNewBreaks(request: BreakRequest): Boolean {
        val iterator = breaks.iterator()
        while (iterator.hasNext()) {
            val ctx = iterator.next()

            if (!canAccept(ctx)) continue

            initNewBreak(ctx, request) ?: return false
            iterator.remove()
        }
        return true
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
            if (!primaryInfo.breakConfig.doubleBreak || secondaryBreak != null) {
                if (!primaryInfo.updatedThisTick && tickStage in primaryInfo.breakConfig.breakStageMask) {
                    primaryInfo.cancelBreak()
                    return@let
                } else return null
            }

            if (!primaryInfo.breaking) {
                secondaryBreak = breakInfo.apply { type = BreakType.Secondary }
                return secondaryBreak
            }

            primaryInfo.stopBreakPacket(world, interaction)
            primaryInfo.makeSecondary()
            return@let
        }

        primaryBreak = breakInfo
        setPendingConfigs(request.build)
        return primaryBreak
    }

    /**
     * Begins the post-break logic sequence for the given [info].
     *
     * [BreakConfirmationMode.None] Will assume the block has been broken server side, and will only persist
     * the [info] if the requester has any un-triggered callbacks. E.G. if the block has broken, but the item hasn't dropped
     * and the requester has specified an itemDrop callback.
     *
     * [BreakConfirmationMode.BreakThenAwait] Will perform all post block break actions, such as spawning break particles,
     * playing sounds, etc. However, it will store the [info] in the pending interaction collections before triggering the
     * [BreakInfo.internalOnBreak] callback, in case the server rejects the break.
     *
     * [BreakConfirmationMode.AwaitThenBreak] Will immediately place the [info] into the pending interaction collections.
     * Once the server responds, confirming the break, the post break actions will take place, and the [BreakInfo.internalOnBreak]
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
                    ReBreakManager.offerReBreak(info)
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
        breaksThisTick++
        info.nullify()
    }

    /**
     * Makes the [BreakInfo] a secondary if not already.
     */
    private fun BreakInfo.makeSecondary() {
        if (secondaryBreak === this) return
        secondaryBreak = this.apply {
            type = BreakType.Secondary
        }
        primaryBreak = null
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
            if (isRedundant || abandoned) return@runSafe
            if (isPrimary) {
                nullify()
                setBreakingTextureStage(player, world, -1)
                abortBreakPacket(world, interaction)
                request.onCancel?.invoke(context.blockPos)
            } else if (isSecondary) {
                if (breakConfig.unsafeCancels) {
                    type = BreakType.RedundantSecondary
                    setBreakingTextureStage(player, world, -1)
                    request.onCancel?.invoke(context.blockPos)
                } else {
                    abandoned = true
                }
            }
        }

    /**
     * Nullifies the break. If the block is not broken, the [BreakInfo.internalOnCancel] callback gets triggered
     */
    private fun BreakInfo.nullify() = type.nullify()

    /**
     * Nullifies the [BreakInfo] reference in the [breakInfos] array based on the [BreakType]
     */
    private fun BreakType.nullify() =
        when (this) {
            Primary,
            ReBreak -> primaryBreak = null
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

        if (!info.breaking) {
            if (!startBreaking(info)) {
                info.nullify()
                info.request.onCancel?.invoke(ctx.blockPos)
                return false
            }
            val swing = config.swing
            if (swing.isEnabled() && (swing != BreakConfig.SwingMode.End || info.isReBreaking)) {
                swingHand(config.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        info.progressedThisTick = true
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
        if (blockState.isEmpty || blockState.isAir) {
            info.nullify()
            info.request.onCancel?.invoke(ctx.blockPos)
            return false
        }

        info.breakingTicks++
        val progress = blockState.calcBreakDelta(
            player,
            world,
            ctx.blockPos,
            config
        ) * (info.breakingTicks - config.fudgeFactor)

        val overBreakThreshold = progress >= info.getBreakThreshold()

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
        if (overBreakThreshold && (!info.isSecondary || info.serverBreakTicks >= info.breakConfig.fudgeFactor + 1)) {
            if (info.isPrimary) {
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

        if (info.couldReBreak.value == true) when (val reBreakResult = ReBreakManager.handleUpdate(info.context, info.request)) {
            is ReBreakResult.StillBreaking -> {
                primaryBreak = reBreakResult.breakInfo.apply {
                    type = Primary
                    ReBreakManager.clearReBreak()
                    request.onStart?.invoke(ctx.blockPos)
                }

                primaryBreak?.let { primary ->
                    updateBreakProgress(primary)
                }
                return true
            }
            is ReBreakResult.ReBroke -> {
                info.type = ReBreak
                info.nullify()
                info.request.onReBreak?.invoke(ctx.blockPos)
                return true
            }
            else -> {}
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
        val notEmpty = blockState.isNotEmpty
        if (notEmpty && info.breakingTicks == 0) {
            blockState.onBlockBreakStart(world, ctx.blockPos, player)
        }

        val breakDelta = blockState.calcBreakDelta(player, world, ctx.blockPos, info.breakConfig)
        info.vanillaInstantBreakable = breakDelta >= 1
        if (notEmpty && breakDelta >= info.getBreakThreshold()) {
            onBlockBreak(info)
            if (!info.vanillaInstantBreakable) breakCooldown = info.breakConfig.breakDelay
        } else {
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

        if (info.isSecondary || (!info.vanillaInstantBreakable && breakDelta >= info.breakConfig.breakThreshold)) {
            info.stopBreakPacket(world, interaction)
        }

        return true
    }

    private fun BlockState.calcBreakDelta(
        player: ClientPlayerEntity,
        world: BlockView,
        pos: BlockPos,
        config: BreakConfig,
        item: ItemStack? = null
    ) = runSafe {
        val delta = calcItemBlockBreakingDelta(player, world, pos, item ?: player.inventory.mainHandStack)
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
