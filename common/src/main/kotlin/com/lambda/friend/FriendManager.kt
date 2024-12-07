/*
 * Copyright 2024 Lambda
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

package com.lambda.friend

import com.lambda.config.Configurable
import com.lambda.config.configurations.FriendConfig
import com.lambda.core.Loadable
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import java.util.*

object FriendManager : Configurable(FriendConfig), Loadable {
    override val name = "friends"
    val friends by setting("friends", setOf<GameProfile>())

    fun add(profile: GameProfile) = friends.add(profile)

    fun remove(profile: GameProfile): Boolean = friends.remove(profile)

    fun gameProfile(name: String) = friends.firstOrNull { it.name == name }
    fun gameProfile(uuid: UUID) = friends.firstOrNull { it.id == uuid }

    fun contains(profile: GameProfile) = friends.contains(profile)
    fun contains(name: String) = friends.any { it.name == name }
    fun contains(uuid: UUID) = friends.any { it.id == uuid }

    fun clear() = friends.clear()

    val OtherClientPlayerEntity.isFriend: Boolean
        get() = contains(gameProfile)

    fun OtherClientPlayerEntity.befriend() = add(gameProfile)
    fun OtherClientPlayerEntity.unfriend() = remove(gameProfile)

    override fun load(): String {
        // TODO: Because the settings are loaded after the property and the loadables, the friend list is empty at that point
        return "Loaded ${friends.size} friends"
    }
}
