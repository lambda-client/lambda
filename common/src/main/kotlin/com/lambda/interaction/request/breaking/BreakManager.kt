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

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.request.rotation.RotationManager.onRotatePost
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemUtils.block
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
import net.minecraft.util.math.BlockPos

object BreakManager : RequestHandler<BreakRequest>() {
    private var primaryBreakingInfo: BreakInfo?
        get() = breakingInfos[0]
        set(value) { breakingInfos[0] = value }
    private var secondaryBreakingInfo: BreakInfo?
        get() = breakingInfos[1]
        set(value) { breakingInfos[1] = value }
    private val breakingInfos = arrayOfNulls<BreakInfo>(2)

    private val pendingInteractions = LimitedDecayQueue<BreakInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out") }

    val blockedPositions
        get() = breakingInfos.mapNotNull { it?.context?.expectedPos } + pendingInteractions.map { it.context.expectedPos }

    private var blockBreakingCooldown = 0

    private var instantBreaks = listOf<BreakContext>()

    private var rotation: RotationRequest? = null
    private var validRotation = false

    fun Any.onBreak(
        alwaysListen: Boolean = false,
        block: SafeContext.() -> Unit
    ) = listen<UpdateManagerEvent.Break.Pre>(0, alwaysListen) { block() }

    fun Any.onBreakPost(
        alwaysListen: Boolean = false,
        block: SafeContext.() -> Unit
    ) = listen<UpdateManagerEvent.Break.Post>(0, alwaysListen) { block() }

    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            if (isOnBreakCooldown()) {
                blockBreakingCooldown--
                return@listen
            }
            if (PlaceManager.activeThisTick()) return@listen

            currentRequest?.let request@ { request ->
                if (instantBreaks.isEmpty()) return@request

                instantBreaks.forEach { ctx ->
                    val breakInfo = with(request) {
                        handleRequestContext(
                            ctx,
                            buildConfig, rotationConfig, hotbarConfig,
                            onBreak, onItemDrop
                        )
                    } ?: return@request
                    if (!breakInfo.requestHotbarSwap()) return@forEach
                    updateBlockBreakingProgress(breakInfo)
                    activeThisTick = true
                }
                instantBreaks = emptyList()
            }

            if (!validRotation) return@listen

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
        }

        onRotate(priority = Int.MIN_VALUE) {
            preEvent()

            if (!updateRequest { true }) {
                requestRotate()
                return@onRotate
            }

            currentRequest?.let request@ { request ->
                val breakConfig = request.buildConfig.breakSettings
                val takeCount = breakConfig.maxPendingBreaks - (breakingInfos.count { it != null } + pendingInteractions.size)
                val validContexts = request.contexts
                    .filter { ctx -> canAccept(ctx) }
                    .sortedBy { it.instantBreak }
                    .take(takeCount)

                instantBreaks = validContexts
                    .take(breakConfig.breaksPerTick)
                    .filter { it.instantBreak }

                if (instantBreaks.isNotEmpty()) return@request

                validContexts
                    .filter { it.instantBreak.not() }
                    .forEach { ctx ->
                        val breakInfo = with(request) {
                            handleRequestContext(
                                ctx,
                                buildConfig, rotationConfig, hotbarConfig,
                                onBreak, onItemDrop
                            )
                        }
                        if (breakInfo == null) return@request
                    }
            }

            requestRotate()
        }

        onRotatePost {
            validRotation = rotation?.done ?: true
            postEvent()
        }

        //ToDo: Clean this up
        listen<WorldEvent.BlockUpdate.Server> { event ->
            pendingInteractions
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { pending ->
                    pendingInteractions.remove(pending)
                    if (!matchesTargetState(event.pos, pending.context.targetState, event.newState)) return@listen
                    if (pending.breakConfig.breakConfirmation == BreakConfirmationMode.AwaitThenBreak)
                        destroyBlock(pending)
                    pending.onBreak()
                }
                ?: breakingInfos
                    .filterNotNull()
                    .firstOrNull { it.context.expectedPos == event.pos }
                    ?.let { info ->
                        if (!matchesTargetState(event.pos, info.context.targetState, event.newState)) return@listen
                        info.nullify()
                        destroyBlock(info)
                        info.onBreak()
                    }
        }

        //ToDo: drop callback stuff
        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
//        listen<EntityEvent.EntityUpdate> {
//            if (it.entity !is ItemEntity) return@listen
//            pendingInteractions
//                .firstOrNull { info -> matchesBlockItem(info, it.entity) }
//                ?.onItemDrop?.invoke(it.entity)
//                ?: breakingInfos
//                    .filterNotNull()
//                    .firstOrNull { info -> matchesBlockItem(info, it.entity) }?.onItemDrop?.invoke(it.entity)
//        }
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
                info.rotationConfig.request(info.context.rotation)
            }
    }

    private fun handleRequestContext(
        requestCtx: BreakContext,
        buildConfig: BuildConfig,
        rotationConfig: RotationConfig,
        hotbarConfig: HotbarConfig,
        onBreak: () -> Unit,
        onItemDrop: (ItemEntity) -> Unit
    ): BreakInfo? {
        val breakInfo = BreakInfo(requestCtx, BreakType.Primary,
            buildConfig.breakSettings, rotationConfig, hotbarConfig,
            onBreak, onItemDrop
        )
        primaryBreakingInfo?.let { primaryInfo ->
            if (!primaryInfo.breakConfig.doubleBreak
                || primaryInfo.startedWithSecondary
                || secondaryBreakingInfo != null) {
                return null
            }

            if (!primaryInfo.breaking) {
                secondaryBreakingInfo = breakInfo.apply { type = BreakType.Secondary }
                return secondaryBreakingInfo
            }

            primaryInfo.type = BreakType.Secondary
            secondaryBreakingInfo = primaryInfo
            primaryBreakingInfo = breakInfo

            setPendingInteractionsLimits(buildConfig)
            return primaryBreakingInfo
        }

        primaryBreakingInfo = breakInfo
        setPendingInteractionsLimits(buildConfig)
        return primaryBreakingInfo
    }

    private fun setPendingInteractionsLimits(buildConfig: BuildConfig) {
        pendingInteractions.setMaxSize(buildConfig.maxPendingInteractions)
        pendingInteractions.setDecayTime(buildConfig.interactionTimeout * 50L)
    }

    private fun SafeContext.canAccept(ctx: BreakContext) =
        pendingInteractions.none { it.context.expectedPos == ctx.expectedPos }
                && breakingInfos.none { info -> info?.context?.expectedPos == ctx.expectedPos }
                && !blockState(ctx.expectedPos).isAir

    private fun SafeContext.updateBlockBreakingProgress(info: BreakInfo): Boolean {
        val ctx = info.context
        val hitResult = ctx.result

        if (interaction.currentGameMode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
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
            if (info.breakConfig.swing != BreakConfig.SwingMode.End) swingHand(info.breakConfig.swingType)
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
            mc.particleManager.addBlockBreakingParticles(
                ctx.expectedPos,
                hitResult.side
            )
        }

        if (info.breakConfig.breakingTexture) {
            setBreakingTextureStage(info)
        }

        if (progress >= info.getBreakThreshold()) {
            interaction.sendSequencedPacket(world) { sequence ->
                onBlockBreak(info)
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
            if (info.breakConfig.swing != BreakConfig.SwingMode.Start) swingHand(info.breakConfig.swingType)
            setBreakCooldown(info.breakConfig.breakDelay)
        } else {
            if (info.breakConfig.swing == BreakConfig.SwingMode.Constant) swingHand(info.breakConfig.swingType)
        }

        return true
    }

    private fun SafeContext.attackBlock(info: BreakInfo): Boolean {
        val ctx = info.context

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, interaction.currentGameMode)) return false
        if (!world.worldBorder.contains(ctx.expectedPos)) return false

        if (interaction.currentGameMode.isCreative) {
            interaction.sendSequencedPacket(world) { sequence: Int ->
                onBlockBreak(info)
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, ctx.expectedPos, ctx.result.side, sequence)
            }
            setBreakCooldown(info.breakConfig.breakDelay)
            return true
        }
        if (info.breaking) return false

        val blockState = blockState(ctx.expectedPos)
        val pendingUpdateManager = world.pendingUpdateManager.incrementSequence()
        val sequence = pendingUpdateManager.sequence
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
            ctx.stopBreakPacket(sequence, connection)
            ctx.startBreakPacket(sequence + 1, connection)
            ctx.stopBreakPacket(sequence + 1, connection)
            repeat(2) {
                pendingUpdateManager.incrementSequence()
            }
        } else {
            ctx.startBreakPacket(sequence, connection)
            if (breakingDelta < 1  && (breakingDelta >= 0.7 || info.breakConfig.doubleBreak)) {
                ctx.stopBreakPacket(sequence + 1, connection)
                pendingUpdateManager.incrementSequence()
            }
        }

        return true
    }

    private fun SafeContext.onBlockBreak(info: BreakInfo) {
        when (info.breakConfig.breakConfirmation) {
            BreakConfirmationMode.None -> {
                destroyBlock(info)
                info.onBreak()
            }
            BreakConfirmationMode.BreakThenAwait -> {
                destroyBlock(info)
                pendingInteractions.add(info)
            }
            BreakConfirmationMode.AwaitThenBreak -> {
                pendingInteractions.add(info)
            }
        }
        info.nullify()
    }

    private fun SafeContext.destroyBlock(info: BreakInfo): Boolean {
        val ctx = info.context

        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, interaction.currentGameMode)) return false

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
        world.setBlockBreakingInfo(
            player.id,
            info.context.expectedPos,
            stage
        )
    }

    private fun isOnBreakCooldown() = blockBreakingCooldown > 0
    private fun setBreakCooldown(cooldown: Int) {
        blockBreakingCooldown = cooldown
    }

    data class BreakInfo(
        val context: BreakContext,
        var type: BreakType,
        val breakConfig: BreakConfig,
        val rotationConfig: RotationConfig,
        val hotbarConfig: HotbarConfig,
        val onBreak: () -> Unit,
        val onItemDrop: (ItemEntity) -> Unit
    ) {
        var breaking = false
        var breakingTicks = 0
        var soundsCooldown = 0.0f
        var startedWithSecondary = false

        fun requestHotbarSwap() =
            hotbarConfig.request(HotbarRequest(context.hotbarIndex)).done

        fun getBreakTextureProgress(player: PlayerEntity, world: ClientWorld): Int {
            val breakDelta = context.checkedState.calcItemBlockBreakingDelta(
                player,
                world,
                context.expectedPos,
                player.mainHandStack
            )

            val progress = (breakDelta * breakingTicks) / breakConfig.breakThreshold
            return if (progress > 0.0f) (progress * 10.0f).toInt() else -1
        }

        fun nullify() = type.nullify()

        fun getBreakThreshold() =
            type.getBreakThreshold(breakConfig)
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