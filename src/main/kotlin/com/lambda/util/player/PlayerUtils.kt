/*
 * Copyright 2026 Lambda
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

package com.lambda.util.player

import com.lambda.config.blocks.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.util.world.fastEntitySearch
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.world.GameMode

const val FAKE_PLAYER_ID = -2024-4-20

val SafeContext.gamemode: GameMode
    get() = interaction.currentGameMode

context(safeContext: SafeContext)
val ClientPlayerEntity.hasFirework: Boolean
    get() = safeContext.fastEntitySearch<FireworkRocketEntity>(4.0) { it.shooter == this }.any()

fun SafeContext.copyPlayer(entity: ClientPlayerEntity) =
    ClientPlayerEntity(mc, world, mc.networkHandler, null, null, entity.lastPlayerInput, entity.isSprinting).apply {
        setPos(entity.x, entity.y, entity.z)
        setExperience(entity.experienceProgress, entity.totalExperience, entity.experienceLevel)
        health = entity.health
        absorptionAmount = entity.absorptionAmount
        pitch = entity.pitch
        yaw = entity.yaw
        headYaw = entity.headYaw
        bodyYaw = entity.bodyYaw
        velocity = entity.velocity
        movementSpeed = entity.movementSpeed
        isSneaking = entity.isSneaking
        isSprinting = entity.isSprinting
        isSwimming = entity.isSwimming
        isOnGround = entity.isOnGround
    }

fun SafeContext.swingHand(swingType: BuildConfig.SwingType, hand: Hand) =
    when (swingType) {
        BuildConfig.SwingType.Vanilla -> {
            swingHandClient(hand)
            connection.sendPacket(HandSwingC2SPacket(hand))
        }
        BuildConfig.SwingType.Server -> connection.sendPacket(HandSwingC2SPacket(hand))
        BuildConfig.SwingType.Client -> swingHandClient(hand)
    }

fun SafeContext.swingHandClient(hand: Hand) {
    if (!player.handSwinging || player.handSwingTicks >= player.handSwingDuration / 2 || player.handSwingTicks < 0) {
        player.handSwingTicks = -1
        player.handSwinging = true
        player.preferredHand = hand
    }
}

fun SafeContext.isItemOnCooldown(stack: ItemStack) = player.itemCooldownManager.isCoolingDown(stack)
