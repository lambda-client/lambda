package com.lambda.friend

import com.lambda.friend.FriendRegistry.friends
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

object FriendManager {
    fun add(profile: GameProfile) = friends.add(profile)

    fun remove(profile: GameProfile) = friends.remove(profile)

    fun get(name: String) = friends.firstOrNull { it.name == name }
    fun get(uuid: UUID) = friends.firstOrNull { it.id == uuid }

    fun contains(profile: GameProfile) = friends.contains(profile)
    fun contains(name: String) = friends.any { it.name == name }
    fun contains(uuid: UUID) = friends.any { it.id == uuid }

    fun clear() = friends.clear()

    val ServerPlayerEntity.isFriend: Boolean
        get() = contains(gameProfile)

    fun ServerPlayerEntity.befriend() = add(gameProfile)
    fun ServerPlayerEntity.unfriend() = remove(gameProfile)
}
