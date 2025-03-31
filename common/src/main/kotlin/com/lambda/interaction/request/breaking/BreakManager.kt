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
import com.lambda.event.events.TickEvent
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
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.module.modules.client.TaskFlowModule
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
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object BreakManager : RequestHandler<BreakRequest>(), PositionBlocking {
    private var primaryBreakingInfo: BreakInfo?
        get() = breakingInfos[0]
        set(value) { breakingInfos[0] = value }
    private var secondaryBreakingInfo: BreakInfo?
        get() = breakingInfos[1]
        set(value) { breakingInfos[1] = value }
    private val breakingInfos = arrayOfNulls<BreakInfo>(2)

    private val pendingBreaks = LimitedDecayQueue<BreakInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out")
        if (!it.broken && it.breakConfig.breakConfirmation != BreakConfirmationMode.AwaitThenBreak) {
            mc.world?.setBlockState(it.context.expectedPos, it.context.checkedState)
        }
        it.pendingInteractionsList.remove(it.context)
    }

    override val blockedPositions
        get() = breakingInfos.mapNotNull { it?.context?.expectedPos } + pendingBreaks.map { it.context.expectedPos }

    private var rotation: RotationRequest? = null
    private val validRotation
        get() = rotation?.done ?: true

    private var blockBreakingCooldown = 0

    private var instantBreaks = listOf<BreakContext>()

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
        listen<TickEvent.Pre>(priority = Int.MIN_VALUE + 1) {
            preEvent()

            if (updateRequest()) currentRequest?.let request@ { request ->
                if (isOnBreakCooldown()) {
                    blockBreakingCooldown--
                    return@request
                }

                val breakConfig = request.buildConfig.breakSettings
                val maxBreaksThisTick = breakConfig.maxPendingBreaks - (breakingInfos.count { it != null } + pendingBreaks.size)
                if (maxBreaksThisTick <= 0) return@request

                val validContexts = request.contexts
                    .filter { ctx -> canAccept(ctx) }
                    .sortedBy { it.instantBreak }
                    .take(maxBreaksThisTick)

                instantBreaks = validContexts
                    .take(breakConfig.instantBreaksPerTick)
                    .filter { it.instantBreak }
                    .sortedBy { it.hotbarIndex == HotbarManager.serverSlot }

                if (instantBreaks.isNotEmpty()) {
                    instantBreaks.forEach { ctx ->
                        if (ctx.hotbarIndex != HotbarManager.serverSlot) {
                            if (!request.hotbarConfig.request(HotbarRequest(ctx.hotbarIndex)).done) return@request
                        }
                        val breakInfo = handleRequestContext(ctx, request) ?: return@request
                        updateBlockBreakingProgress(breakInfo)
                        activeThisTick = true
                    }
                    if (instantBreaks.size == breakConfig.instantBreaksPerTick) {
                        instantBreaks = emptyList()
                        return@request
                    }
                    instantBreaks = emptyList()
                }

                validContexts
                    .filter { it.instantBreak.not() }
                    .forEach { ctx ->
                        if (handleRequestContext(ctx, request) == null) return@request
                    }
            }

            requestRotate()
            if (!validRotation) {
                postEvent()
                return@listen
            }

            // ToDo: dynamically update hotbarIndex as contexts are persistent and don't get updated by new requests each tick
            // Reversed so that the breaking order feels natural to the user as the primary break has to
            // be started after the secondary
            breakingInfos
                .filterNotNull()
                .reversed()
                .forEach { info ->
                    if (!info.requestHotbarSwap()) return@forEach
                    updateBlockBreakingProgress(info)
                    activeThisTick = true
                }

            postEvent()
        }

        listen<WorldEvent.BlockUpdate.Server> { event ->
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

            breakingInfos
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
        listen<EntityEvent.EntityUpdate> {
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

            breakingInfos
                .filterNotNull()
                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
                ?.internalOnItemDrop(it.entity)
        }

        listenUnsafe<ConnectionEvent.Connect.Pre> {
            breakingInfos.forEach { it?.nullify() }
            pendingBreaks.clear()
            setBreakCooldown(0)
        }
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

    private fun requestRotate() {
        rotation = breakingInfos
            .filterNotNull()
            .firstOrNull { it.breakConfig.rotateForBreak }
            ?.let { info ->
                info.rotationConfig.request(RotationRequest(info.context.rotation, info.rotationConfig))
            }
    }

    private fun handleRequestContext(
        requestCtx: BreakContext,
        request: BreakRequest
    ): BreakInfo? {
        val breakInfo = BreakInfo(requestCtx, BreakType.Primary, request)
        primaryBreakingInfo?.let { primaryInfo ->
            if (!primaryInfo.breakConfig.doubleBreak
                || primaryInfo.startedWithSecondary
                || secondaryBreakingInfo != null
                || requestCtx.hotbarIndex != primaryInfo.context.hotbarIndex) {
                return null
            }

            if (!primaryInfo.breaking) {
                secondaryBreakingInfo = breakInfo.apply { type = BreakType.Secondary }
                return secondaryBreakingInfo
            }

            primaryInfo.type = BreakType.Secondary
            secondaryBreakingInfo = primaryInfo
            primaryBreakingInfo = breakInfo

            setPendingInteractionsLimits(request.buildConfig)
            return primaryBreakingInfo
        }

        primaryBreakingInfo = breakInfo
        setPendingInteractionsLimits(request.buildConfig)
        return primaryBreakingInfo
    }

    private fun setPendingInteractionsLimits(buildConfig: BuildConfig) {
        pendingBreaks.setMaxSize(buildConfig.breakSettings.maxPendingBreaks)
        pendingBreaks.setDecayTime(buildConfig.interactionTimeout * 50L)
    }

    private fun SafeContext.canAccept(ctx: BreakContext) =
        pendingBreaks.none { it.context.expectedPos == ctx.expectedPos }
                && breakingInfos.none { info -> info?.context?.expectedPos == ctx.expectedPos }
                && !blockState(ctx.expectedPos).isAir

    private fun SafeContext.updateBlockBreakingProgress(info: BreakInfo): Boolean {
        val ctx = info.context
        val hitResult = ctx.result

        if (gamemode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
            setBreakCooldown(info.breakConfig.breakDelay)
            interaction.sendSequencedPacket(world) { sequence ->
                onBlockBreak(info)
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
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
            setBreakingTextureStage(info)
        }

        val swing = info.breakConfig.swing

        if (progress >= info.getBreakThreshold()) {
            interaction.sendSequencedPacket(world) { sequence ->
                onBlockBreak(info)
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
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
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, ctx.expectedPos, ctx.result.side, sequence)
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

        val breakingDelta = blockState.calcItemBlockBreakingDelta(player, world, ctx.expectedPos, player.mainHandStack)
        if (notAir && breakingDelta >= info.getBreakThreshold()) {
            onBlockBreak(info)
        } else {
            info.apply {
                breaking = true
                breakingTicks = 1
                soundsCooldown = 0.0f
            }
            if (info.breakConfig.breakingTexture) {
                setBreakingTextureStage(info)
            }
            if (secondaryBreakingInfo != null)
                primaryBreakingInfo?.startedWithSecondary = true
        }

        if (info.breakConfig.breakMode == BreakMode.Packet) {
            ctx.stopBreakPacket(world, interaction)
            ctx.startBreakPacket(world, interaction)
            ctx.stopBreakPacket(world, interaction)
        } else {
            ctx.startBreakPacket(world, interaction)
            if (breakingDelta < 1  && (breakingDelta >= 0.7 || info.breakConfig.doubleBreak)) {
                ctx.stopBreakPacket(world, interaction)
            }
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

        if (info.breakConfig.breakingTexture) setBreakingTextureStage(info, -1)

        return setState
    }

    private fun SafeContext.setBreakingTextureStage(
        info: BreakInfo,
        stage: Int = info.getBreakTextureProgress(player, world)
    ) {
        world.setBlockBreakingInfo(player.id, info.context.expectedPos, stage)
    }

    private fun isOnBreakCooldown() = blockBreakingCooldown > 0
    private fun setBreakCooldown(cooldown: Int) {
        blockBreakingCooldown = cooldown
    }

    data class BreakInfo(
        val context: BreakContext,
        var type: BreakType,
        val request: BreakRequest
    ) {
        val breakConfig = request.buildConfig.breakSettings
        val rotationConfig = request.rotationConfig
        private val hotbarConfig = request.hotbarConfig
        val pendingInteractionsList = request.pendingInteractionsList
        private val onBreak = request.onBreak
        private val onItemDrop = request.onItemDrop

        var breaking = false
        var breakingTicks = 0
        var soundsCooldown = 0.0f
        var startedWithSecondary = false

        @Volatile
        var broken = false
            private set
        private var item: ItemEntity? = null

        val callbacksCompleted
            @Synchronized get() = broken && (onItemDrop == null || item != null)

        fun internalOnBreak() {
            synchronized(this) {
                broken = true
                onBreak()
                item?.let { item ->
                    onItemDrop?.invoke(item)
                }
            }
        }

        fun internalOnItemDrop(item: ItemEntity) {
            synchronized(this) {
                this.item = item
                if (broken) {
                    onItemDrop?.invoke(item)
                }
            }
        }

        fun requestHotbarSwap() =
            hotbarConfig.request(HotbarRequest(context.hotbarIndex)).done

        fun getBreakTextureProgress(player: PlayerEntity, world: ClientWorld): Int {
            val breakDelta = context.checkedState.calcItemBlockBreakingDelta(player, world, context.expectedPos, player.mainHandStack)
            val progress = (breakDelta * breakingTicks) / breakConfig.breakThreshold
            return if (progress > 0.0f) (progress * 10.0f).toInt() else -1
        }

        fun nullify() = type.nullify()

        fun getBreakThreshold() = type.getBreakThreshold(breakConfig)
    }

    enum class BreakType(val index: Int) {
        Primary(0),
        Secondary(1);

        fun getBreakThreshold(breakConfig: BreakConfig) =
            when (this) {
                Primary -> breakConfig.breakThreshold
                Secondary -> 1.0f
            }

        fun nullify() =
            when (this) {
                Primary -> primaryBreakingInfo = null
                Secondary -> secondaryBreakingInfo = null
            }
    }

    override fun preEvent() = UpdateManagerEvent.Break.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Break.Post().post()
}