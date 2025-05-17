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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.breaking.BreakManager.activeRequest
import com.lambda.interaction.request.breaking.BreakManager.processRequest
import com.lambda.interaction.request.breaking.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakType.ReBreak
import com.lambda.interaction.request.breaking.BrokenBlockHandler.brokenState
import com.lambda.interaction.request.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.interaction.request.breaking.BrokenBlockHandler.isBroken
import com.lambda.interaction.request.breaking.BrokenBlockHandler.pendingBreaks
import com.lambda.interaction.request.breaking.BrokenBlockHandler.setPendingConfigs
import com.lambda.interaction.request.breaking.BrokenBlockHandler.startPending
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.Communication.warn
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.gamemode
import com.lambda.util.player.swingHand
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.ItemEntity
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object BreakManager : RequestHandler<BreakRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Player.Post,
    // ToDo: Post interact
    onOpen = { processRequest(activeRequest) }
), PositionBlocking {
    private var primaryBreak: BreakInfo?
        get() = breakInfos[0]
        set(value) { breakInfos[0] = value }
    private var secondaryBreak: BreakInfo?
        get() = breakInfos[1]
        set(value) { breakInfos[1] = value }
    private val breakInfos = arrayOfNulls<BreakInfo>(2)

    private val pendingBreakCount get() = breakInfos.count { it != null } + pendingBreaks.size
    override val blockedPositions
        get() = breakInfos.mapNotNull { it?.context?.expectedPos } + pendingBreaks.map { it.context.expectedPos }

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
        priority: Priority = 0,
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
                info?.apply {
                    if (isRedundant) updateBreakProgress(this)
                    else if (!updatedThisTick) {
                        this.cancelBreak()
                        return@apply
                    }
                    tickStats()
                }
            }
            activeRequest = null
            breaks = mutableListOf()
            instantBreaks = mutableListOf()
            breaksThisTick = 0
        }

        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            if (event.pos == ReBreakManager.reBreak?.context?.expectedPos) return@listen

            breakInfos
                .filterNotNull()
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { info ->
                    // if not broken
                    if (!isBroken(info.context.checkedState, event.newState)) {
                        this@BreakManager.warn("Break at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${info.context.checkedState.brokenState}")
                        // update the checked state
                        info.context.checkedState = event.newState
                        return@listen
                    }
                    destroyBlock(info)
                    info.internalOnBreak()
                    if (!info.callbacksCompleted) {
                        info.startPending()
                    } else {
                        ReBreakManager.offerReBreak(info)
                    }
                    info.nullify()
                }
        }

        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
        listen<EntityEvent.Update>(priority = Int.MIN_VALUE) {
            if (it.entity !is ItemEntity) return@listen

            ReBreakManager.reBreak?.let { reBreak ->
                if (matchesBlockItem(reBreak, it.entity)) return@listen
            }

            breakInfos
                .filterNotNull()
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.internalOnItemDrop(it.entity)
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
        if (activeRequest != null || PlaceManager.activeThisTick || request.contexts.isEmpty()) return

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
        pendingBreaks.cleanUp()

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
                            if (instantBreaks.isEmpty()) info.request.rotation.request(rotation, false) else rotation
                        }
                }
                .asReversed()
                .forEach { info ->
                    if (info.updatedProgressThisTick) return@forEach
                    if (!info.context.requestDependencies(info.request)) return@run
                    if (tickStage !in info.breakConfig.breakStageMask) return@forEach
                    if ((!rotated && info.isPrimary)) return@run

                    updateBreakProgress(info)
                }
        }

        if (instantBreaks.isEmpty() && breaks.isEmpty()) {
            activeRequest = null
        }
        if (breaksThisTick > 0 || breakInfos.any { it != null && !it.isRedundant }) {
            activeThisTick = true
        }
    }

    /**
     * Filters the requests [BreakContext]s, and iterates over the [breakInfos] collection looking for matches
     * in positions. If a match is found, the [BreakInfo] is updated with the new context. Otherwise, the break is cancelled.
     * The [instantBreaks] and [breaks] collections are then populated with the new appropriate contexts, and the [maxBreaksThisTick]
     * value is set.
     *
     * @see canAccept
     * @see cancelBreak
     */
    private fun SafeContext.populateFrom(request: BreakRequest) {
        // Sanitize the new breaks
        val newBreaks = request.contexts
            .filter { ctx -> canAccept(ctx, request.build.breaking) }
            .toMutableList()

        // Update the current break infos or cancel if abandoned
        breakInfos
            .filterNotNull()
            .forEach { info ->
                newBreaks.find { ctx -> ctx.expectedPos == info.context.expectedPos }?.let { ctx ->
                    if (!info.updatedThisTick) info.updateInfo(ctx, request)
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

        val breakConfig = request.build.breaking
        val pendingLimit = (breakConfig.maxPendingBreaks - pendingBreakCount).coerceAtLeast(0)
        maxBreaksThisTick = breakConfig.breaksPerTick.coerceAtMost(pendingLimit)
    }

    /**
     * @return if the break context can be accepted.
     */
    private fun SafeContext.canAccept(ctx: BreakContext, breakConfig: BreakConfig): Boolean {
        if (pendingBreaks.any { it.context.expectedPos == ctx.expectedPos }) return false

        if (breakConfig.doubleBreak) {
            breakInfos
                .firstOrNull { it != null && !it.isRedundant }
                ?.let { info ->
                    if (ctx.hotbarIndex != info.context.hotbarIndex) return false
                }
        }

        return !blockState(ctx.expectedPos).isAir
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

            if (!ctx.requestDependencies(request)) return false
            rotationRequest = if (request.build.breaking.rotateForBreak) request.rotation.request(ctx.rotation, false) else null
            if (!rotated || tickStage !in request.build.breaking.breakStageMask) return false

            val breakInfo = initNewBreak(ctx, request) ?: return false
            request.onAccept?.invoke(ctx.expectedPos)
            updateBreakProgress(breakInfo)
            breaksThisTick++
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
     * @see atMaxBreakInfos
     */
    private fun SafeContext.processNewBreaks(request: BreakRequest): Boolean {
        val iterator = breaks.iterator()
        while (iterator.hasNext()) {
            val ctx = iterator.next()
            initNewBreak(ctx, request) ?: return false
            request.onAccept?.invoke(ctx.expectedPos)
            iterator.remove()
            if (atMaxBreakInfos(request.build.breaking)) return false
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
        val breakInfo = BreakInfo(requestCtx, Primary, request)
        primaryBreak?.let { primaryInfo ->
            if (!breakInfo.breakConfig.doubleBreak || secondaryBreak != null) {
                if (!primaryInfo.updatedThisTick) {
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
        setPendingConfigs(request)
        return primaryBreak
    }

    /**
     * @return if the [breakInfos] are at capacity and no new breaks can be started.
     */
    private fun atMaxBreakInfos(breakConfig: BreakConfig): Boolean {
        val possibleBreakingCount = if (breakConfig.doubleBreak) 2 else 1
        return breakInfos.take(possibleBreakingCount).all { it != null }
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
     *
     * @see makeRedundant
     */
    private fun BreakInfo.cancelBreak() =
        runSafe {
            setBreakingTextureStage(player, world, -1)
            if (isPrimary) {
                abortBreakPacket(world, interaction)
                nullify()
            } else if (isSecondary && breakConfig.unsafeCancels) {
                makeRedundant()
            }

            internalOnCancel()
        }

    /**
     * Nullifies the break. If the block is not broken, the [BreakInfo.internalOnCancel] callback gets triggered
     */
    private fun BreakInfo.nullify() = type.nullify()

    /**
     * Makes the [BreakInfo] redundant and triggers the [BreakInfo.internalOnCancel] callback
     */
    private fun BreakInfo.makeRedundant() {
        type = BreakType.RedundantSecondary
    }

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
        info.updatedProgressThisTick = true
        val ctx = info.context
        val hitResult = ctx.result

        if (gamemode.isCreative && world.worldBorder.contains(ctx.expectedPos) && info.breaking) {
            if (info.isRedundant) {
                onBlockBreak(info)
                return true
            }
            breakCooldown = info.breakConfig.breakDelay
            lastPosStarted = ctx.expectedPos
            onBlockBreak(info)
            interaction.sendSequencedPacket(world) { sequence ->
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
            val swing = info.breakConfig.swing
            if (swing.isEnabled()) {
                swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        if (!info.breaking) {
            when (val reBreakResult = ReBreakManager.handleUpdate(info.context, info.request)) {
                is ReBreakResult.StillBreaking -> {
                    primaryBreak = reBreakResult.breakInfo.apply {
                        type = Primary
                        ReBreakManager.clearReBreak()
                        request.onAccept?.invoke(ctx.expectedPos)
                    }

                    return primaryBreak?.let { primary ->
                        updateBreakProgress(primary)
                    } ?: false
                }
                is ReBreakResult.ReBroke -> {
                    info.type = ReBreak
                    info.nullify()
                    info.request.onReBreak?.invoke(info.context.expectedPos)
                    return true
                }
                else -> {}
            }
            if (!startBreaking(info)) {
                info.nullify()
                info.internalOnCancel()
                return false
            }
            val swing = info.breakConfig.swing
            if (swing.isEnabled() && swing != BreakConfig.SwingMode.End) {
                swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        val blockState = blockState(ctx.expectedPos)
        if (blockState.isAir) {
            info.nullify()
            info.internalOnCancel()
            return false
        }

        info.breakingTicks++
        val progress = blockState.calcItemBlockBreakingDelta(
            player,
            world,
            ctx.expectedPos,
            player.mainHandStack
        ) * info.breakingTicks

        val overBreakThreshold = progress >= info.getBreakThreshold()

        if (info.isRedundant) {
            if (overBreakThreshold) {
                onBlockBreak(info)
            }
            return true
        }

        if (info.breakConfig.sounds) {
            if (info.soundsCooldown % 4.0f == 0.0f) {
                val blockSoundGroup = blockState.soundGroup
                mc.soundManager.play(
                    PositionedSoundInstance(
                        blockSoundGroup.hitSound,
                        SoundCategory.BLOCKS,
                        (blockSoundGroup.getVolume() + 1.0f) / 8.0f,
                        blockSoundGroup.getPitch() * 0.5f,
                        SoundInstance.createRandom(),
                        ctx.expectedPos
                    )
                )
            }
            info.soundsCooldown++
        }

        if (info.breakConfig.particles) {
            mc.particleManager.addBlockBreakingParticles(ctx.expectedPos, hitResult.side)
        }

        if (info.breakConfig.breakingTexture) {
            info.setBreakingTextureStage(player, world)
        }

        val swing = info.breakConfig.swing
        if (overBreakThreshold) {
            if (info.isPrimary) {
                onBlockBreak(info)
                interaction.sendSequencedPacket(world) { sequence ->
                    PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
                }
            } else {
                onBlockBreak(info)
            }
            if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
            breakCooldown = info.breakConfig.breakDelay
        } else {
            if (swing == BreakConfig.SwingMode.Constant) swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
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

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, gamemode)) return false
        if (!world.worldBorder.contains(ctx.expectedPos)) return false

        if (gamemode.isCreative) {
            lastPosStarted = ctx.expectedPos
            onBlockBreak(info)
            interaction.sendSequencedPacket(world) { sequence: Int ->
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, ctx.result.side, sequence)
            }
            breakCooldown = info.breakConfig.breakDelay
            return true
        }
        if (info.breaking) return false

        lastPosStarted = ctx.expectedPos

        val blockState = blockState(ctx.expectedPos)
        val notAir = !blockState.isAir
        if (notAir && info.breakingTicks == 0) {
            blockState.onBlockBreakStart(world, ctx.expectedPos, player)
        }

        val breakDelta = blockState.calcItemBlockBreakingDelta(player, world, ctx.expectedPos, player.mainHandStack)
        if (notAir && breakDelta >= info.getBreakThreshold()) {
            onBlockBreak(info)
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
        info.vanillaInstantBreakable = breakDelta >= 1

        if (info.isSecondary || (!info.vanillaInstantBreakable && breakDelta >= info.breakConfig.breakThreshold)) {
            info.stopBreakPacket(world, interaction)
        }

        return true
    }

    /**
     * @return if the [ItemEntity] matches the [BreakInfo]'s expected item drop.
     */
    fun matchesBlockItem(info: BreakInfo, entity: ItemEntity): Boolean {
        val inRange = info.context.expectedPos.toCenterPos().isInRange(entity.pos, 0.5)
        val correctMaterial = info.context.checkedState.block == entity.stack.item.block
        return inRange && correctMaterial
    }

    override fun preEvent(): Event = UpdateManagerEvent.Break().post()
}
