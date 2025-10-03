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
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Rebreak
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.RedundantSecondary
import com.lambda.interaction.request.breaking.BreakInfo.BreakType.Secondary
import com.lambda.interaction.request.breaking.BreakManager.calcBreakDelta
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action

data class BreakInfo(
    override var context: BreakContext,
    var type: BreakType,
    var request: BreakRequest
) : ActionInfo, LogContext {
    // Delegates
    val breakConfig get() = request.build.breaking
    override val pendingInteractionsList get() = request.pendingInteractions

    // Pre Processing
    var shouldProgress = false
    var rebreakPotential = RebreakHandler.RebreakPotential.None
    var swapInfo = SwapInfo.EMPTY
    var swapStack: ItemStack = ItemStack.EMPTY

    // BreakInfo Specific
    var updatedThisTick = true
    var updatedPreProcessingThisTick = false
    var progressedThisTick = false

    // Processing
    var breaking = false
    var abandoned = false
    var breakingTicks = 0
    var soundsCooldown = 0f
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
    var broken = false; private set
    private var item: ItemEntity? = null
    val callbacksCompleted
        get() = broken && (request.onItemDrop == null || item != null)

    fun internalOnBreak() {
        if (type != Rebreak) broken = true
        item?.let { item ->
            request.onItemDrop?.invoke(item)
        }
    }

    fun internalOnItemDrop(item: ItemEntity) {
        if (type != Rebreak) this.item = item
        if (broken || type == Rebreak) {
            request.onItemDrop?.invoke(item)
        }
    }

    fun updateInfo(context: BreakContext, request: BreakRequest? = null) {
        updatedThisTick = true
        this.context = context
        request?.let { this.request = it }
        if (type == RedundantSecondary) type = Secondary
    }

    fun resetCallbacks() {
        broken = false
        item = null
    }

    fun tickChecks() {
        updatedThisTick = false
        updatedPreProcessingThisTick = false
        progressedThisTick = false
    }

    fun setBreakingTextureStage(
        player: ClientPlayerEntity,
        world: ClientWorld,
        stage: Int = getBreakTextureProgress(player, world)
    ) {
        world.setBlockBreakingInfo(player.id, context.blockPos, stage)
    }

    private fun getBreakTextureProgress(player: ClientPlayerEntity, world: ClientWorld): Int {
        val swapMode = breakConfig.swapMode
        val item =
            if (swapMode.isEnabled() && swapMode != BreakConfig.SwapMode.Start) swapStack else player.mainHandStack
        val breakDelta = context.cachedState.calcBreakDelta(player, world, context.blockPos, breakConfig, item)
        val progress = (breakDelta * breakingTicks) / (getBreakThreshold() + (breakDelta * breakConfig.fudgeFactor))
        return if (progress > 0.0f) (progress * 10.0f).toInt().coerceAtMost(9) else -1
    }

    fun getBreakThreshold() =
        when (type) {
            Primary,
            Rebreak-> breakConfig.breakThreshold
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

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Break Info") {
            value("Type", type)
            text(context.getLogContextBuilder())
            group("Details") {
                value("Should Progress", shouldProgress)
                value("Rebreak Potential", rebreakPotential)
                text(swapInfo.getLogContextBuilder())
                value("Swap Stack", swapStack)
                value("Updated This Tick", updatedThisTick)
                value("Updated Pre-Processing This Tick", updatedPreProcessingThisTick)
                value("Progressed This Tick", progressedThisTick)
                value("Breaking", breaking)
                value("Abandoned", abandoned)
                value("Breaking Ticks", breakingTicks)
                value("Sounds Cooldown", soundsCooldown)
                value("Vanilla Instant Breakable", vanillaInstantBreakable)
                value("Rebreakable", rebreakable)
            }
        }
    }

    override fun toString() = "$type, ${context.cachedState}, ${context.blockPos}"
}
