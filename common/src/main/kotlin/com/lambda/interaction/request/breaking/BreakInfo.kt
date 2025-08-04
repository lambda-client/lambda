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

import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.request.ActionInfo
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.world.WorldView

data class BreakInfo(
    override var context: BreakContext,
    var type: BreakType,
    var request: BreakRequest
) : ActionInfo {
    val breakConfig get() = request.build.breaking
    override val pendingInteractionsList get() = request.pendingInteractions

    var updatedThisTick = true
    var progressedThisTick = false
    var serverBreakTicks = 0

    var couldReBreak = lazy {
        runSafe {
            ReBreakManager.couldReBreak(this@BreakInfo, player, world)
        } == true
    }

    var breaking = false
    var abandoned = false
    var breakingTicks = 0
    var soundsCooldown = 0.0f

    var vanillaInstantBreakable = false
    val reBreakable get() = !vanillaInstantBreakable && isPrimary

    val isPrimary get() = type == BreakType.Primary
    val isSecondary get() = type == BreakType.Secondary
    val isRedundant get() = type == BreakType.RedundantSecondary
    val isReBreaking get() = type == BreakType.ReBreak

    @Volatile
    var broken = false; private set
    private var item: ItemEntity? = null

    val callbacksCompleted
        @Synchronized get() = broken && (request.onItemDrop == null || item != null)

    @Synchronized
    fun internalOnBreak() {
        if (!isReBreaking) broken = true
        item?.let { item ->
            request.onItemDrop?.invoke(item)
        }
    }

    @Synchronized
    fun internalOnItemDrop(item: ItemEntity) {
        if (!isReBreaking) this.item = item
        if (broken || isReBreaking) {
            request.onItemDrop?.invoke(item)
        }
    }

    fun updateInfo(context: BreakContext, request: BreakRequest? = null) {
        updatedThisTick = true
        this.context = context
        request?.let { this.request = it }
        if (isRedundant) type = BreakType.Secondary
    }

    fun tickStats() {
        updatedThisTick = false
        progressedThisTick = false
    }

    fun resetCallbacks() {
        broken = false
        item = null
    }

    fun shouldSwap(player: ClientPlayerEntity, world: WorldView): Boolean {
        val item = player.inventory.getStack(context.hotbarIndex)
        val breakDelta = context.cachedState.calcItemBlockBreakingDelta(player, world, context.blockPos, item)
        val breakProgress = breakDelta * ((breakingTicks + 1) - breakConfig.fudgeFactor).let {
            if (isSecondary) it + 1 else it
        }
        return if (couldReBreak.value)
            breakConfig.swapMode.isEnabled()
        else when (breakConfig.swapMode) {
            BreakConfig.SwapMode.None -> false
            BreakConfig.SwapMode.Start -> !breaking
            BreakConfig.SwapMode.End -> breakProgress >= getBreakThreshold()
            BreakConfig.SwapMode.StartAndEnd -> !breaking || breakProgress >= getBreakThreshold()
            BreakConfig.SwapMode.Constant -> true
        }
    }

    fun setBreakingTextureStage(
        player: ClientPlayerEntity,
        world: ClientWorld,
        stage: Int = getBreakTextureProgress(player, world)
    ) {
        world.setBlockBreakingInfo(player.id, context.blockPos, stage)
    }

    private fun getBreakTextureProgress(player: PlayerEntity, world: ClientWorld): Int {
        val breakDelta = context.cachedState.calcItemBlockBreakingDelta(player, world, context.blockPos, player.mainHandStack)
        val progress = (breakDelta * breakingTicks) / (getBreakThreshold() + (breakDelta * breakConfig.fudgeFactor))
        return if (progress > 0.0f) (progress * 10.0f).toInt().coerceAtMost(10) else -1
    }

    fun getBreakThreshold() = type.getBreakThreshold(breakConfig)

    fun startBreakPacket(world: ClientWorld, interaction: ClientPlayerInteractionManager) =
        breakPacket(Action.START_DESTROY_BLOCK, world, interaction)

    fun stopBreakPacket(world: ClientWorld, interaction: ClientPlayerInteractionManager) =
        breakPacket(Action.STOP_DESTROY_BLOCK, world, interaction)

    fun abortBreakPacket(world: ClientWorld, interaction: ClientPlayerInteractionManager) =
        breakPacket(Action.ABORT_DESTROY_BLOCK, world, interaction)

    private fun breakPacket(action: Action, world: ClientWorld, interaction: ClientPlayerInteractionManager) =
        interaction.sendSequencedPacket(world) { sequence: Int ->
            PlayerActionC2SPacket(
                action,
                context.blockPos,
                context.result.side,
                sequence
            )
        }
}

enum class BreakType() {
    Primary,
    Secondary,
    RedundantSecondary,
    ReBreak;

    fun getBreakThreshold(breakConfig: BreakConfig) =
        when (this) {
            Primary -> breakConfig.breakThreshold
            else -> 1.0f
        }
}
