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

package com.lambda.module.modules.combat

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.player.spawnFakePlayer
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.entity.Entity
import java.util.*
import kotlin.concurrent.fixedRateTimer

object FakePlayer : Module(
    name = "FakePlayer",
    description = "Spawns a fake player",
    defaultTags = setOf(ModuleTag.COMBAT, ModuleTag.RENDER)
) {
    private val playerName by setting("Name", "Steve")
    private val fetchKey get() = playerName.lowercase() // Nicknames aren't case-sensitive

    private var fakePlayer: OtherClientPlayerEntity? = null; set(value) {
        runSafe {
            field?.let {
                world.removeEntity(it.id, Entity.RemovalReason.DISCARDED)
            }
            value?.let {
                world.addEntity(it)
            }
        }

        field = value
    }

    private val nilUuid = UUID(0, 0)
    private val cachedProfiles = hashMapOf<String, GameProfile>()

    init {
        listen<TickEvent.Pre> {
            fakePlayer = cachedProfiles[fetchKey]?.let { cached ->
                // Keep fetched fake player
                fakePlayer?.gameProfile?.also { profile ->
                    if (profile is FetchedGameProfile && profile.name == cached.name) return@let fakePlayer
                }

                // Spawn fetched fake player
                spawnFakePlayer(
                    profile = cached,
                    reference = fakePlayer ?: player,
                    addToWorld = false
                )
            } ?: fakePlayer?.takeIf { it.gameProfile.name == playerName } ?: spawnFakePlayer(
                // Spawn offline fake player while fetching
                profile = GameProfile(nilUuid, playerName),
                reference = fakePlayer ?: player,
                addToWorld = false
            )
        }

        fixedRateTimer(
            name = "FakePlayer profile fetcher",
            daemon = true,
            initialDelay = 0L,
            period = 2000L
        ) {
            cachedProfiles[fetchKey] ?: runSafe {
                val (requestedProfile, _) =
                    Fuel.get("https://api.mojang.com/users/profiles/minecraft/$fetchKey")
                        .responseObject<GameProfile>().third

                val uuid = requestedProfile?.id ?: nilUuid

                val fetchedProperties = mc.sessionService.fetchProfile(uuid, true)?.profile?.properties

                val profile = FetchedGameProfile(nilUuid, playerName).apply {
                    fetchedProperties?.forEach { key, value -> properties.put(key, value) }
                }

                runGameScheduled {
                    // This is the cache that mc pulls profile data from when it fetches skins.
                    mc.networkHandler?.playerListEntries?.put(profile.id, PlayerListEntry(profile, false))
                    cachedProfiles[fetchKey] = profile
                }
            }
        }

        onDisable {
            fakePlayer = null
        }

        onShutdown {
            disable()
        }

        listen<ConnectionEvent.Connect.Pre> {
            disable()
        }

        listen<ConnectionEvent.Disconnect> {
            disable()
        }
    }

    private class FetchedGameProfile(id: UUID, name: String) : GameProfile(id, name)
}
