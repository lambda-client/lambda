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

import com.lambda.Lambda.mc
import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.breaking.BreakType.Primary
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.BlockUtils.matches
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.gamemode
import com.lambda.util.player.swingHand
import net.minecraft.block.BlockState
import net.minecraft.block.OperatorBlock
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.ItemEntity
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos

object BreakManager : RequestHandler<BreakRequest>(), PositionBlocking {
    private var primaryBreak: BreakInfo?
        get() = breakInfos[0]
        set(value) { breakInfos[0] = value }
    private var secondaryBreak: BreakInfo?
        get() = breakInfos[1]
        set(value) { breakInfos[1] = value }
    private val breakInfos = arrayOfNulls<BreakInfo>(2)
    private var liveBreakInfos = listOf<BreakInfo>()

    private val pendingBreaks = LimitedDecayQueue<BreakInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info ->
        mc.world?.let { world ->
            val pos = info.context.expectedPos
            val loaded = world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.x), ChunkSectionPos.getSectionCoord(pos.z))
            if (!loaded) return@let

            info("${info::class.simpleName} at ${info.context.expectedPos.toShortString()} timed out")

            val awaitThenBreak = info.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak
            if (!info.broken && awaitThenBreak) {
                world.setBlockState(info.context.expectedPos, info.context.checkedState)
            }
        }
        info.pendingInteractionsList.remove(info.context)
    }

    override val blockedPositions
        get() = breakInfos.mapNotNull { it?.context?.expectedPos } + pendingBreaks.map { it.context.expectedPos }

    private var blockBreakingCooldown = 0

    private var hotbarRequest: HotbarRequest? = null
    private val swapped get() = hotbarRequest?.done == true

    private var rotation: RotationRequest? = null
    private val validRotation get() = rotation?.done != false

    private var newBreaks = mutableListOf<BreakContext>()
    private var instantBreaks = listOf<BreakContext>()
    private var excessInstantBreaks = false

    fun Any.onBreak(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Break.Pre>(priority, alwaysListen) {
        block()
    }

    fun Any.onBreakPost(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Break.Post>(priority, alwaysListen) {
        block()
    }

    init {
        listen<UpdateManagerEvent.Hotbar.Pre> {
            preEvent()

            pendingBreaks.cleanUp()
            updateRequest()
            val hotbarConfig = currentRequest?.hotbarConfig
                ?: breakInfos.firstOrNull { it != null }?.request?.hotbarConfig
                ?: return@listen

            hotbarRequest = HotbarRequest(hotbarConfig) { if (update(::swapTo, tickPre = true)) done() }
            hotbarRequest?.let { hotbarRequest ->
                hotbarConfig.request(hotbarRequest)
            }
            if (instantBreaks.isNotEmpty() || liveBreakInfos.isNotEmpty()) {
                activeThisTick = true
            }
        }

        listen<MovementEvent.Player.Post> {
            val sequenceMode = currentRequest?.buildConfig?.breakSettings?.sequenceMode
                ?: breakInfos.find { it != null }?.breakConfig?.sequenceMode
                ?: return@listen
            if (sequenceMode == BuildConfig.InteractSequenceMode.PostMovement) {
                update(null)
            }
        }

        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE + 1) { event ->
            pendingBreaks
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { pending ->
                    // return if the state hasn't changed
                    if (event.newState.matches(pending.context.checkedState))
                        return@listen

                    // return if the block's not broken
                    if (!matchesTargetState(event.pos, pending.context.targetState, event.newState)) {
                        removePendingBreak(pending)
                        return@listen
                    }

                    if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak) {
                        destroyBlock(pending)
                    }
                    pending.internalOnBreak()
                    if (pending.callbacksCompleted) {
                        removePendingBreak(pending)
                    }
                    return@listen
                }

            breakInfos
                .filterNotNull()
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { info ->
                    // if not broken
                    if (!matchesTargetState(event.pos, info.context.targetState, event.newState)) {
                        // update the checked state
                        info.context.checkedState = event.newState
                        return@listen
                    }
                    destroyBlock(info)
                    info.internalOnBreak()
                    if (!info.callbacksCompleted) {
                        addPendingBreak(info)
                    }
                    info.nullify()
                }
        }

        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
        listen<EntityEvent.EntityUpdate>(priority = Int.MIN_VALUE + 1) {
            if (it.entity !is ItemEntity) return@listen
            pendingBreaks
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.let { pending ->
                    pending.internalOnItemDrop(it.entity)
                    if (pending.callbacksCompleted) {
                        removePendingBreak(pending)
                    }
                    return@listen
                }

            breakInfos
                .filterNotNull()
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.internalOnItemDrop(it.entity)
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE + 1) {
            breakInfos.forEach { it?.nullify() }
            pendingBreaks.clear()
            setBreakCooldown(0)
        }
    }

    private fun SafeContext.update(swapTo: ((slot: Int) -> Boolean)?, tickPre: Boolean = false): Boolean {
        if (tickPre) {
            if (isOnBreakCooldown()) {
                blockBreakingCooldown--
            } else if (currentRequest == null) {
                breakInfos.forEach { it?.cancelBreak() }
            }
        }

        if (!isOnBreakCooldown()) currentRequest?.let request@ { request ->
            val breakConfig = request.buildConfig.breakSettings

            if (tickPre) {
                newBreaks = request.contexts
                    .filter { ctx -> canAccept(ctx) }
                    .sortedWith(
                        compareByDescending<BreakContext> { it.instantBreak }
                            .thenByDescending { it.hotbarIndex == HotbarManager.serverSlot }
                    ).toMutableList()

                refreshOrCancelBreaks(newBreaks, request)

                val maxBreaks = getMaxBreaks(breakConfig).coerceAtLeast(0)
                val maxInstantBreaks = breakConfig.instantBreaksPerTick.coerceAtMost(maxBreaks)

                val uncappedInstantBreaks = newBreaks.filter { it.instantBreak }
                instantBreaks = uncappedInstantBreaks.take(maxInstantBreaks)
                excessInstantBreaks = uncappedInstantBreaks.size > instantBreaks.size
            }

            if (atMaxBreakInfos(breakConfig)) return@request
            instantBreaks.forEach { ctx ->
                swapTo?.invoke(ctx.hotbarIndex)
                val mismatchedTiming = tickPre && breakConfig.sequenceMode != BuildConfig.InteractSequenceMode.TickStart
                if (mismatchedTiming) return false
                if (!swapped) return@request
                val breakInfo = handleNewBreak(ctx, request) ?: return@request
                request.onAccept?.invoke(ctx.expectedPos)
                updateBreakProgress(breakInfo)
            }
            if (!excessInstantBreaks) processNewBreaks(newBreaks, request)
        }

        updateLiveBreakInfos()
        rotation = liveBreakInfos.firstOrNull { it.breakConfig.rotateForBreak }?.let { info ->
            info.rotationConfig.request(info.context.rotation)
        }

        // Reversed so that the breaking order feels natural to the user as the primary break has to
        // be started after the secondary
        liveBreakInfos
            .reversed()
            .forEach { info ->
                swapTo?.invoke(info.context.hotbarIndex)
                val mismatchedTiming = tickPre && info.breakConfig.sequenceMode != BuildConfig.InteractSequenceMode.TickStart
                if (!swapped || !validRotation || mismatchedTiming) return false
                updateBreakProgress(info)
            }

        return true
    }

    private fun refreshOrCancelBreaks(newContexts: MutableCollection<BreakContext>, request: BreakRequest) {
        breakInfos
            .forEachNotNull { info ->
                newContexts.find { ctx -> ctx.expectedPos == info.context.expectedPos }?.let { ctx ->
                    info.updateInfo(ctx, request)
                    newContexts.remove(ctx)
                    return@forEachNotNull
                }

                info.cancelBreak()
            }
    }

    private fun SafeContext.processNewBreaks(newBreaks: Collection<BreakContext>, request: BreakRequest) {
        newBreaks
            .filter { !it.instantBreak }
            .forEach { ctx ->
                handleNewBreak(ctx, request) ?: return
                request.onAccept?.invoke(ctx.expectedPos)
                if (atMaxBreakInfos(request.buildConfig.breakSettings)) return
            }
    }

    private fun SafeContext.updateLiveBreakInfos() {
        liveBreakInfos = breakInfos
            .filterNotNull()
            .filter {
                if (it.redundant) {
                    updateBreakProgress(it)
                    false
                } else true
            }
    }

    private fun getMaxBreaks(breakConfig: BreakConfig): Int =
        breakConfig.maxPendingBreaks - (breakInfos.count { it != null } + pendingBreaks.size)

    private fun atMaxBreakInfos(breakConfig: BreakConfig): Boolean {
        val possibleBreakingCount = if (breakConfig.doubleBreak) 2 else 1
        return breakInfos.take(possibleBreakingCount).all { it != null }
    }

    private fun matchesBlockItem(info: BreakInfo, entity: ItemEntity): Boolean {
        val inRange = info.context.expectedPos.toCenterPos().isInRange(entity.pos, 0.5)
        val correctMaterial = info.context.checkedState.block == entity.stack.item.block
        return inRange && correctMaterial
    }

    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@BreakManager.warn("Break at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }

    private fun SafeContext.handleNewBreak(
        requestCtx: BreakContext,
        request: BreakRequest
    ): BreakInfo? {
        val breakInfo = BreakInfo(requestCtx, Primary, request)
        primaryBreak?.let { primaryInfo ->
            if (!breakInfo.breakConfig.doubleBreak || secondaryBreak != null) {
                primaryInfo.abortBreakPacket(world, interaction)
                return@let
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
        setPendingBreaksLimits(request.buildConfig)
        return primaryBreak
    }

    private fun setPendingBreaksLimits(buildConfig: BuildConfig) {
        pendingBreaks.setMaxSize(buildConfig.breakSettings.maxPendingBreaks)
        pendingBreaks.setDecayTime(buildConfig.interactionTimeout * 50L)
    }

    private fun SafeContext.canAccept(ctx: BreakContext): Boolean {
        if (pendingBreaks.any { it.context.expectedPos == ctx.expectedPos }) return false

        breakInfos.firstOrNull { it != null && !it.redundant }
            ?.let { info ->
                if ( ctx.hotbarIndex != info.context.hotbarIndex) return false
            }

        return !blockState(ctx.expectedPos).isAir
    }

    private fun SafeContext.updateBreakProgress(info: BreakInfo): Boolean {
        val ctx = info.context
        val hitResult = ctx.result

        if (gamemode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
            if (info.redundant) {
                onBlockBreak(info)
                return true
            }
            setBreakCooldown(info.breakConfig.breakDelay)
            interaction.sendSequencedPacket(world) { sequence ->
                onBlockBreak(info)
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
            val swing = info.breakConfig.swing
            if (swing.isEnabled()) {
                swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
            }
            return true
        }

        if (!info.breaking) {
            if (!attackBlock(info)) {
                info.nullify()
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

        if (info.redundant) {
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
            if (info.type == Primary) {
                interaction.sendSequencedPacket(world) { sequence ->
                    onBlockBreak(info)
                    PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
                }
            } else {
                onBlockBreak(info)
            }
            if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
            setBreakCooldown(info.breakConfig.breakDelay)
        } else {
            if (swing == BreakConfig.SwingMode.Constant) swingHand(info.breakConfig.swingType, Hand.MAIN_HAND)
        }

        return true
    }

    private fun SafeContext.attackBlock(info: BreakInfo): Boolean {
        val ctx = info.context

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, gamemode)) return false
        if (!world.worldBorder.contains(ctx.expectedPos)) return false

        if (gamemode.isCreative) {
            interaction.sendSequencedPacket(world) { sequence: Int ->
                onBlockBreak(info)
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, ctx.result.side, sequence)
            }
            setBreakCooldown(info.breakConfig.breakDelay)
            return true
        }
        if (info.breaking) return false

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
        if (info.type == BreakType.Secondary || (breakDelta < 1  && breakDelta >= info.breakConfig.breakThreshold)) {
            info.stopBreakPacket(world, interaction)
        }

        return true
    }

    private fun SafeContext.onBlockBreak(info: BreakInfo) {
        when (info.breakConfig.breakConfirmation) {
            BreakConfirmationMode.None -> {
                destroyBlock(info)
                info.internalOnBreak()
                if (!info.callbacksCompleted) {
                    addPendingBreak(info)
                }
            }
            BreakConfirmationMode.BreakThenAwait -> {
                destroyBlock(info)
                addPendingBreak(info)
            }
            BreakConfirmationMode.AwaitThenBreak -> {
                addPendingBreak(info)
            }
        }
        info.nullify()
    }

    private fun addPendingBreak(info: BreakInfo) {
        pendingBreaks.add(info)
        info.pendingInteractionsList.add(info.context)
    }

    private fun removePendingBreak(info: BreakInfo) {
        pendingBreaks.remove(info)
        info.pendingInteractionsList.remove(info.context)
    }

    private fun SafeContext.destroyBlock(info: BreakInfo): Boolean {
        val ctx = info.context

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, gamemode)) return false

        if (!player.mainHandStack.item.canMine(ctx.checkedState, world, ctx.expectedPos, player))
            return false
        val block = ctx.checkedState.block
        if (block is OperatorBlock && !player.isCreativeLevelTwoOp) return false
        if (ctx.checkedState.isAir) return false

        block.onBreak(world, ctx.expectedPos, ctx.checkedState, player)
        val fluidState = fluidState(ctx.expectedPos)
        val setState = world.setBlockState(ctx.expectedPos, fluidState.blockState, 11)
        if (setState) block.onBroken(world, ctx.expectedPos, ctx.checkedState)

        if (info.breakConfig.breakingTexture) info.setBreakingTextureStage(player, world, -1)

        return setState
    }

    private fun isOnBreakCooldown() = blockBreakingCooldown > 0
    private fun setBreakCooldown(cooldown: Int) {
        blockBreakingCooldown = cooldown
    }

    private fun BreakInfo.makeSecondary() {
        if (secondaryBreak === this) return
        secondaryBreak = this.apply {
            type = BreakType.Secondary
        }
        primaryBreak = null
    }

    private fun BreakInfo.cancelBreak() =
        runSafe {
            setBreakingTextureStage(player, world, -1)
            if (type == Primary) {
                abortBreakPacket(world, interaction)
                nullify()
                return@runSafe
            }
            if (type == BreakType.Secondary && breakConfig.unsafeCancels) {
                makeRedundant()
            }
        }

    private fun BreakInfo.nullify() {
        type.nullify()
        if (!broken) internalOnCancel()
    }

    private fun BreakInfo.makeRedundant() {
        makeSecondary()
        type = BreakType.RedundantSecondary
        internalOnCancel()
    }

    private fun BreakType.nullify() =
        when (this) {
            Primary -> primaryBreak = null
            else -> secondaryBreak = null
        }

    private fun Array<BreakInfo?>.forEachNotNull(block: (BreakInfo) -> Unit) {
        for (info in this) info?.run(block)
    }

    override fun preEvent() = UpdateManagerEvent.Break.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Break.Post().post()
}