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

package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.network.mojang.getProfile
import com.lambda.threading.onShutdown
import com.lambda.util.Timer
import com.lambda.util.player.PlayerUtils.FAKE_PLAYER_ID
import com.mojang.authlib.GameProfile
import com.mojang.datafixers.util.Either
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
object FakePlayer : Module(
    name = "FakePlayer",
    description = "Spawns a fake player",
    tag = ModuleTag.COMBAT,
) {
    private val playerName by setting("Name", "Steve")

    private var fakePlayer_field: OtherClientPlayerEntity? = null
    private var SafeContext.fakePlayer
        get() = fakePlayer_field
        set(value) { fakePlayer_field = value; value?.let { world.addEntity(it) } }

    private val nilProfile: GameProfile
        get() = GameProfile(UUID(0, 0), playerName)

    private val cachedProfiles = mutableMapOf<String, GameProfile>()
    private val fetchTimer = Timer()

    init {
        listen<TickEvent.Pre> {
            val newFakePlayer = cachedProfiles[playerName]
                ?.let { newFakePlayer(it) }
                ?.takeUnless { it.gameProfile == fakePlayer?.gameProfile } // If the current profile equals the current fake player's profile, stop.

            fakePlayer = newFakePlayer
                ?: return@listen
        }

        listenConcurrently<TickEvent.Pre>({ 1000 }) {
            if (!fetchTimer.timePassed(2.seconds)) return@listenConcurrently
            cachedProfiles.getOrPut(playerName) { fetchProfile(playerName) }
        }

        listen<PlayerEvent.Attack.Entity> {
            if (it.entity.id == FAKE_PLAYER_ID) it.cancel()
        }

        listen<ConnectionEvent.Connect.Pre> { disable() }

        onShutdown { disable() } // FixMe: This doesn't work because the hook triggers after the modules are saved.

        onDisable { fakePlayer?.discard(); fakePlayer = null }
    }

    fun SafeContext.newFakePlayer(profile: GameProfile) =
        OtherClientPlayerEntity(world, profile).apply {
            copyFrom(player)
            id = FAKE_PLAYER_ID
        }

    suspend fun SafeContext.fetchProfile(user: String): GameProfile {
        val requestedProfile = getProfile(user)
            .getOrElse { return nilProfile }

        // Fetch the skin properties from mojang
        val properties = mc.apiServices.profileResolver
            .getProfile(Either.right(requestedProfile.id))
            .getOrNull()
            ?.properties
            ?: return nilProfile

        val profile = GameProfile(requestedProfile.id, requestedProfile.name, properties)

	    connection.playerListEntries[profile.id] = PlayerListEntry(profile, false)

	    return profile
    }
}
