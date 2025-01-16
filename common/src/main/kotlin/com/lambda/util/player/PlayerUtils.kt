package com.lambda.util.player

import com.lambda.context.SafeContext
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry

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

fun SafeContext.spawnFakePlayer(profile: GameProfile): OtherClientPlayerEntity {
    val entity = OtherClientPlayerEntity(world, profile).apply {
            copyFrom(player)

            playerListEntry = PlayerListEntry(profile, false)
            id = -2024 - 4 - 20
        }

    world.addEntity(entity)

    return entity
}
