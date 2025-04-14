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
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action

data class BreakInfo(
    var context: BreakContext,
    var type: BreakType,
    var request: BreakRequest
) {
    val breakConfig get() = request.build.breaking
    val pendingInteractions get() = request.pendingInteractions

    var activeAge = 0
    var updatedThisTick = true
    var updatedProgressThisTick = false

    var breaking = false
    var breakingTicks = 0
    var soundsCooldown = 0.0f

    val isPrimary get() = type == BreakType.Primary
    val isSecondary get() = type == BreakType.Secondary
    val isRedundant get() = type == BreakType.RedundantSecondary

    @Volatile
    var broken = false; private set
    private var item: ItemEntity? = null

    val callbacksCompleted
        @Synchronized get() = broken && (request.onItemDrop == null || item != null)

    fun internalOnBreak() {
        synchronized(this) {
            broken = true
            request.onBreak?.invoke(context.expectedPos)
            item?.let { item ->
                request.onItemDrop?.invoke(item)
            }
        }
    }

    fun internalOnItemDrop(item: ItemEntity) {
        synchronized(this) {
            this.item = item
            if (broken) {
                request.onItemDrop?.invoke(item)
            }
        }
    }

    fun internalOnCancel() {
        request.onCancel?.invoke(context.expectedPos)
    }

    fun updateInfo(context: BreakContext, request: BreakRequest) {
        updatedThisTick = true
        this.context = context
        this.request = request
        if (isRedundant) {
            type = BreakType.Secondary
        }
    }

    fun setBreakingTextureStage(
        player: ClientPlayerEntity,
        world: ClientWorld,
        stage: Int = getBreakTextureProgress(player, world)
    ) {
        world.setBlockBreakingInfo(player.id, context.expectedPos, stage)
    }

    private fun getBreakTextureProgress(player: PlayerEntity, world: ClientWorld): Int {
        val breakDelta = context.checkedState.calcItemBlockBreakingDelta(player, world, context.expectedPos, player.mainHandStack)
        val progress = (breakDelta * breakingTicks) / getBreakThreshold()
        return if (progress > 0.0f) (progress * 10.0f).toInt() else -1
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
                context.expectedPos,
                context.result.side,
                sequence
            )
        }
}

enum class BreakType(val index: Int) {
    Primary(0),
    Secondary(1),
    RedundantSecondary(2);

    fun getBreakThreshold(breakConfig: BreakConfig) =
        when (this) {
            Primary -> breakConfig.breakThreshold
            else -> 1.0f
        }
}