package com.lambda.friend

import com.lambda.config.Configurable
import com.lambda.config.configurations.FriendConfig
import com.lambda.core.Loadable
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

object FriendManager : Configurable(FriendConfig), Loadable {
    override val name = "FriendManager"

    private var friends by setting("friends", listOf<GameProfile>())

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

    override fun load(): String {
        if (friends.isEmpty()) return "No friends loaded, you don't have to be antisocial online too,"
        val word = if (friends.size == 1) "friend" else "friends"
        return "Loaded ${friends.size} $word."
    }
}
