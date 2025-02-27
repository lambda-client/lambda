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
import com.lambda.interaction.request.RequestConfig
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.block.OperatorBlock
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.sound.SoundCategory

object BreakManager : RequestHandler<BreakRequest>() {
    var buildConfig: BuildConfig = TaskFlowModule.build
    var primaryBreakingInfo: BreakInfo?
        get() = breakingInfos[0]
        set(value) { breakingInfos[0] = value }
    var secondaryBreakingInfo: BreakInfo?
        get() = breakingInfos[1]
        set(value) { breakingInfos[1] = value }
    val breakingInfos = arrayOfNulls<BreakInfo>(2)

    val pendingInteractions = LimitedDecayQueue<BreakInfo>(
        buildConfig.maxPendingInteractions, buildConfig.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out"); it.nullify() }

    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            if (updateRequest(true) { true }) {
                currentRequest?.contexts?.forEach { requestCtx ->
                    if (requestCtx == null) return@forEach
                    if (!canAccept(requestCtx)) return@forEach

                    primaryBreakingInfo?.let { primaryInfo ->
                        if (!buildConfig.breakSettings.doubleBreak) return@let
                        if (primaryInfo.startedWithSecondary) return@let
                        secondaryBreakingInfo = BreakInfo.SecondaryBreakInfo(requestCtx)
                    } ?: run {
                        primaryBreakingInfo = BreakInfo.PrimaryBreakInfo(requestCtx)
                    }
                }
            }

            for (it in breakingInfos.reversed()) {
                if (interaction.blockBreakingCooldown > 0) {
                    interaction.blockBreakingCooldown--
                    break
                }
                it?.let { info ->
                    if (pendingInteractions.contains(info)) return@let
                    updateBlockBreakingProgress(info, player.mainHandStack)
                    if (info is BreakInfo.SecondaryBreakInfo)
                        primaryBreakingInfo?.startedWithSecondary = true
                }
            }
        }

        listen<WorldEvent.BlockUpdate.Server>(alwaysListen = true) { event ->
            var breakBlock = false
            val info = pendingInteractions
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.also {
                    pendingInteractions.remove(it)
                    if (buildConfig.breakSettings.breakConfirmation == BreakConfirmationMode.AwaitThenBreak)
                        breakBlock = true
                }
                ?: breakingInfos
                    .firstOrNull { it?.context?.expectedPos == event.pos }
                    ?.also {
                        breakBlock = true
                    }
                ?: return@listen

            info.nullify()

            if (!info.context.targetState.matches(event.newState, event.pos, world)) {
                this@BreakManager.warn("Update at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${info.context.targetState}")
                return@listen
            }
            if (breakBlock) {
                destroyBlock(info)
            }
            currentRequest?.onBreak()
        }
    }

    fun canAccept(ctx: BreakContext) =
        pendingInteractions.none { it.context.expectedPos == ctx.expectedPos }
                && breakingInfos.none { info -> info?.context?.expectedPos == ctx.expectedPos }

    override fun updateRequest(
        keepIfNull: Boolean,
        filter: (Map.Entry<RequestConfig<BreakRequest>, BreakRequest>) -> Boolean
    ): Boolean {
        val updatedCurrentRequest = super.updateRequest(keepIfNull, filter)
        buildConfig = currentRequest?.primaryContext?.buildConfig ?: TaskFlowModule.build
        return updatedCurrentRequest
    }

    private fun SafeContext.updateBlockBreakingProgress(info: BreakInfo, item: ItemStack): Boolean {
        val ctx = info.context
        val hitResult = ctx.result

        if (interaction.currentGameMode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
            interaction.blockBreakingCooldown = ctx.buildConfig.breakSettings.breakDelay
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

        if (ctx.buildConfig.breakSettings.sounds) {
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

        if (ctx.buildConfig.breakSettings.particles) {
            mc.particleManager.addBlockBreakingParticles(
                ctx.expectedPos,
                hitResult.side
            )
        }

        if (progress >= info.getBreakThreshold()) {
            interaction.sendSequencedPacket(world) { sequence ->
                onBlockBreak(info)
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
        }

        if (ctx.buildConfig.breakSettings.breakingTexture) {
            setBreakingTextureStage(info)
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
            interaction.blockBreakingCooldown = buildConfig.breakSettings.breakDelay
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
            if (ctx.buildConfig.breakSettings.breakingTexture) {
                setBreakingTextureStage(info)
            }
        }

        if (ctx.buildConfig.breakSettings.breakMode == BreakMode.Packet) {
            ctx.stopBreakPacket(sequence, connection)
            ctx.startBreakPacket(sequence + 1, connection)
            ctx.stopBreakPacket(sequence + 1, connection)
            repeat(2) {
                pendingUpdateManager.incrementSequence()
            }
        } else {
            ctx.startBreakPacket(sequence, connection)
            if (breakingDelta < 1) {
                ctx.stopBreakPacket(sequence + 1, connection)
                pendingUpdateManager.incrementSequence()
            }
        }

        return true
    }

    private fun SafeContext.onBlockBreak(info: BreakInfo) {
        when (info.context.buildConfig.breakSettings.breakConfirmation) {
            BreakConfirmationMode.None -> {
                destroyBlock(info)
                currentRequest?.onBreak()
                info.nullify()
            }
            BreakConfirmationMode.BreakThenAwait -> {
                destroyBlock(info)
                pendingInteractions.add(info)
            }
            BreakConfirmationMode.AwaitThenBreak -> {
                pendingInteractions.add(info)
            }
        }
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

        if (ctx.buildConfig.breakSettings.breakingTexture) setBreakingTextureStage(info, -1)

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

    abstract class BreakInfo(
        val context: BreakContext
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

            val progress = (breakDelta * breakingTicks) / buildConfig.breakSettings.breakThreshold
            return if (progress > 0.0f) (progress * 10.0f).toInt() else -1
        }

        open fun getBreakThreshold() =
            context.buildConfig.breakSettings.breakThreshold

        open fun nullify() {}

        class PrimaryBreakInfo(
            ctx: BreakContext
        ) : BreakInfo(ctx) {
            override fun nullify() {
                primaryBreakingInfo = null
            }
        }

        class SecondaryBreakInfo(
            ctx: BreakContext
        ) : BreakInfo(ctx) {
            override fun getBreakThreshold() =
                1.0f

            override fun nullify() {
                secondaryBreakingInfo = null
            }
        }
    }
}