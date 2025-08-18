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
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.OneSetPerTick
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action

data class BreakInfo(
    override var context: BreakContext,
    var type: BreakType,
    var request: BreakRequest
) : ActionInfo {
    // Delegates
    val breakConfig get() = request.build.breaking
    override val pendingInteractionsList get() = request.pendingInteractions

    // Pre Processing
    var shouldProgress = false
    var rebreakPotential by OneSetPerTick(value = RebreakManager.RebreakPotential.None, throwOnLimitBreach = true)
    var swapInfo by OneSetPerTick(value = SwapInfo.EMPTY, throwOnLimitBreach = true)
    var swapStack: ItemStack by OneSetPerTick(ItemStack.EMPTY, true)

    // BreakInfo Specific
    var updatedThisTick by OneSetPerTick(false, resetAfterTick = true).apply { set(true) }
    var updatedPreProcessingThisTick by OneSetPerTick(value = false, throwOnLimitBreach = true, resetAfterTick = true)
    var progressedThisTick by OneSetPerTick(value = false, throwOnLimitBreach = true, resetAfterTick = true)

    // Processing
    var breaking = false
    var abandoned = false
    var breakingTicks by OneSetPerTick(0, true)
    var soundsCooldown by OneSetPerTick(0f, true)
    var vanillaInstantBreakable = false
    val rebreakable get() = !vanillaInstantBreakable && type == Primary

    enum class BreakType(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        Primary("Primary", "The main block you’re breaking right now."),
        Secondary("Secondary", "A second block broken at the same time (when double‑break is enabled)."),
        RedundantSecondary("Redundant Secondary", "A previously started secondary break that’s now ignored/monitored only (no new actions)."),
        Rebreak("Rebreak", "A previously broken block which new breaks in the same position can compound progression on. Often rebreaking instantly.");
    }

    // Post Processing
    @Volatile
    var broken = false; private set
    private var item: ItemEntity? = null
    val callbacksCompleted
        @Synchronized get() = broken && (request.onItemDrop == null || item != null)

    @Synchronized
    fun internalOnBreak() {
        if (type != BreakType.Rebreak) broken = true
        item?.let { item ->
            request.onItemDrop?.invoke(item)
        }
    }

    @Synchronized
    fun internalOnItemDrop(item: ItemEntity) {
        if (type != BreakType.Rebreak) this.item = item
        if (broken || type == BreakType.Rebreak) {
            request.onItemDrop?.invoke(item)
        }
    }

    fun updateInfo(context: BreakContext, request: BreakRequest? = null) {
        updatedThisTick = true
        this.context = context
        request?.let { this.request = it }
        if (type == BreakType.RedundantSecondary) type = BreakType.Secondary
    }

    fun resetCallbacks() {
        broken = false
        item = null
    }

    fun setBreakingTextureStage(
        player: ClientPlayerEntity,
        world: ClientWorld,
        stage: Int = getBreakTextureProgress(player, world)
    ) {
        world.setBlockBreakingInfo(player.id, context.blockPos, stage)
    }

    private fun getBreakTextureProgress(player: PlayerEntity, world: ClientWorld): Int {
        val swapMode = breakConfig.swapMode
        val item =
            if (swapMode.isEnabled() && swapMode != BreakConfig.SwapMode.Start) swapStack else player.mainHandStack
        val breakDelta = context.cachedState.calcItemBlockBreakingDelta(player, world, context.blockPos, item)
        val progress = (breakDelta * breakingTicks) / (getBreakThreshold() + (breakDelta * breakConfig.fudgeFactor))
        return if (progress > 0.0f) (progress * 10.0f).toInt().coerceAtMost(9) else -1
    }

    fun getBreakThreshold() =
        when (type) {
            Primary -> breakConfig.breakThreshold
            else -> 1.0f
        }

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
