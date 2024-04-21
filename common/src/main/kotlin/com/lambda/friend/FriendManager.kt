package com.lambda.friend

import com.lambda.config.Configurable
import com.lambda.config.configurations.FriendConfig
import com.lambda.core.Loadable
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.util.world.EntityUtils.getEntities
import com.mojang.authlib.GameProfile
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.event.listener.EntityGameEventHandler
import java.util.Arrays
import java.util.UUID

object FriendManager : Configurable(FriendConfig), Loadable {
    override val name = "FriendManager"

    private val friends by setting("friends", arrayListOf<GameProfile>())

    fun add(vararg profile: GameProfile) =
        friends.addAll(profile)

    fun remove(vararg profile: GameProfile) =
        friends.removeAll(profile.toSet())

    fun get(name: String) = friends.firstOrNull { it.name == name }
    fun get(uuid: UUID) = friends.firstOrNull { it.id == uuid }

    fun contains(profile: GameProfile) = friends.contains(profile)
    fun contains(name: String) = friends.any { it.name == name }
    fun contains(uuid: UUID) = friends.any { it.id == uuid }

    fun clear() = friends.clear()

    val ServerPlayerEntity.isFriend: Boolean
        get() = contains(gameProfile)

    override fun load(): String {
        if (friends.isEmpty()) return "No friends loaded, damn bro you don't have to be antisocial online too,"
        val word = if (friends.size == 1) "friend" else "friends"
        return "Loaded ${friends.size} $word."
    }
}
