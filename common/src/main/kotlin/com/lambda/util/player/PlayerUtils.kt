package com.lambda.util.player

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand

fun SafeContext.copyPlayer(entity: ClientPlayerEntity) =
    ClientPlayerEntity(mc, world, mc.networkHandler, null, null, entity.isSneaking, entity.isSprinting).apply {
        setPos(entity.x, entity.y, entity.z)
        setExperience(entity.experienceProgress, entity.totalExperience, entity.experienceLevel)
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

fun SafeContext.spawnFakePlayer(
    profile: GameProfile,
    reference: PlayerEntity = player,
    addToWorld: Boolean = true
): OtherClientPlayerEntity {
    val entity = OtherClientPlayerEntity(world, profile).apply {
            copyFrom(reference)

            playerListEntry = PlayerListEntry(profile, false)
            id = -2024 - 4 - 20
        }

    if (addToWorld) world.addEntity(entity)

    return entity
}

fun SafeContext.swingHand(swingType: BuildConfig.SwingType) =
    when (swingType) {
        BuildConfig.SwingType.Vanilla -> player.swingHand(player.activeHand)
        BuildConfig.SwingType.Server -> connection.sendPacket(HandSwingC2SPacket(player.activeHand))
        BuildConfig.SwingType.Client -> swingHandClient(player.activeHand)
    }

fun SafeContext.swingHandClient(hand: Hand) {
    if (!player.handSwinging || player.handSwingTicks >= player.handSwingDuration / 2 || player.handSwingTicks < 0) {
        player.handSwingTicks = -1
        player.handSwinging = true
        player.preferredHand = hand
    }
}