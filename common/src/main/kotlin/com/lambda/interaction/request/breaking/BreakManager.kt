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
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.swingHandClient
import net.minecraft.block.BlockState
import net.minecraft.block.OperatorBlock
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
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

    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            if (interaction.blockBreakingCooldown > 0) {
                interaction.blockBreakingCooldown--
                return@listen
            }

            if (updateRequest(false) { true }) {
                currentRequest?.let request@ { request ->
                    var instaBreaks = 0
                    request.contexts
                        .sortedBy { it.instantBreak }
                        .forEach { requestCtx ->
                            if (blockState(requestCtx.expectedPos).isAir) {
                                return@forEach
                            }
                            val infoIndex = handleRequestContext(
                                requestCtx,
                                request.onBreak,
                                request.buildConfig,
                                request.rotationConfig
                            )
                            if (infoIndex == -1) return@request
                            if (requestCtx.instantBreak && instaBreaks < request.buildConfig.breakSettings.breaksPerTick) {
                                breakingInfos.getOrNull(infoIndex)?.let { info ->
                                    updateBlockBreakingProgress(info, player.mainHandStack)
                                    instaBreaks++
                                }
                            }
                        }
                }
            }

            breakingInfos.reversed().filterNotNull().forEach { info ->
                if (info.breakConfig.rotateForBreak && !info.context.rotation.done) return@listen
                updateBlockBreakingProgress(info, player.mainHandStack)
            }
        }

        onRotate {
            breakingInfos
                .filterNotNull()
                .firstOrNull { it.breakConfig.rotateForBreak }?.let { info ->
                    info.rotationConfig.request(info.context.rotation)
                }
        }

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
    }

    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@BreakManager.warn("Break at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }

    private fun handleRequestContext(
        requestCtx: BreakContext,
        onBreak: () -> Unit,
        buildConfig: BuildConfig,
        rotationConfig: RotationConfig
    ): Int {
        if (!canAccept(requestCtx)) return -1

        primaryBreakingInfo?.let { primaryInfo ->
            if (!primaryInfo.breakConfig.doubleBreak) return -1
            if (primaryInfo.startedWithSecondary) return -1
            if (!primaryInfo.breaking) {
                secondaryBreakingInfo = BreakInfo(
                    requestCtx,
                    BreakInfo.BreakType.Secondary,
                    onBreak,
                    buildConfig.breakSettings,
                    rotationConfig
                )
                return 1
            } else {
                primaryInfo.type = BreakInfo.BreakType.Secondary
                secondaryBreakingInfo = primaryInfo
                primaryBreakingInfo = BreakInfo(
                    requestCtx,
                    BreakInfo.BreakType.Primary,
                    onBreak,
                    buildConfig.breakSettings,
                    rotationConfig
                )
                return 0
            }
        } ?: run {
            primaryBreakingInfo = BreakInfo(
                requestCtx,
                BreakInfo.BreakType.Primary,
                onBreak,
                buildConfig.breakSettings,
                rotationConfig
            )
            pendingInteractions.setMaxSize(buildConfig.maxPendingInteractions)
            pendingInteractions.setDecayTime(buildConfig.interactionTimeout * 50L)
            return 0
        }
    }

    private fun canAccept(ctx: BreakContext) =
        pendingInteractions.none { it.context.expectedPos == ctx.expectedPos }
                && breakingInfos.none { info -> info?.context?.expectedPos == ctx.expectedPos }

    private fun SafeContext.updateBlockBreakingProgress(info: BreakInfo, item: ItemStack): Boolean {
        val ctx = info.context
        val hitResult = ctx.result

        if (interaction.currentGameMode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
            interaction.blockBreakingCooldown = info.breakConfig.breakDelay
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
            if (info.breakConfig.swing != BreakConfig.SwingMode.End) swingHand(info)
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
            item
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
            if (info.breakConfig.swing != BreakConfig.SwingMode.Start) swingHand(info)
        } else {
            if (info.breakConfig.swing == BreakConfig.SwingMode.Constant) swingHand(info)
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
            interaction.blockBreakingCooldown = info.breakConfig.breakDelay
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
                breakingTicks = 0
                soundsCooldown = 0.0f
            }
            if (info.breakConfig.breakingTexture) {
                setBreakingTextureStage(info)
            }
        }
        if (info.type == BreakInfo.BreakType.Secondary)
            primaryBreakingInfo?.startedWithSecondary = true

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

    private fun SafeContext.swingHand(info: BreakInfo) {
        when (info.breakConfig.swingType) {
            BreakConfig.SwingType.Vanilla -> player.swingHand(player.activeHand)
            BreakConfig.SwingType.Server -> connection.sendPacket(HandSwingC2SPacket(player.activeHand))
            BreakConfig.SwingType.Client -> swingHandClient(player.activeHand)
        }
    }

    data class BreakInfo(
        val context: BreakContext,
        var type: BreakType,
        val onBreak: () -> Unit,
        val breakConfig: BreakConfig,
        val rotationConfig: RotationConfig
    ) {
        var breaking = false
        var breakingTicks = 0
        var soundsCooldown = 0.0f
        var startedWithSecondary = false

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

        enum class BreakType {
            Primary,
            Secondary;

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
    }
}