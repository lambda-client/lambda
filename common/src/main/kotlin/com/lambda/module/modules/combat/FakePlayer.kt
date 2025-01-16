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

import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafeConcurrent
import com.lambda.threading.runSafeGameScheduled
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.entity.Entity
import java.util.*

object FakePlayer : Module(
    name = "FakePlayer",
    description = "Spawns a fake player",
    defaultTags = setOf(ModuleTag.COMBAT, ModuleTag.RENDER)
) {
    private val playerName by setting("Name", "Steve")

    private var fakePlayer: OtherClientPlayerEntity? = null
    private val nilUuid = UUID(0, 0)

    init {
        onEnable {
            fakePlayer?.let { fake ->
                // Avoid multiple api requests
                if (fake.gameProfile.name == playerName)
                    return@onEnable spawnPlayer(fake.gameProfile)
            }

            runSafeConcurrent {
                val uuid =
                    request("https://api.mojang.com/users/profiles/minecraft/$playerName") {
                        method(Method.GET)
                    }.json<GameProfile>().data?.id ?: nilUuid

                val fetchedProperties = mc.sessionService.fetchProfile(uuid, true)?.profile?.properties

                val profile = GameProfile(nilUuid, playerName).apply {
                    fetchedProperties?.forEach { key, value -> properties.put(key, value) }
                }

                // This is the cache that mc pulls profile data from when it fetches skins.
                mc.networkHandler?.playerListEntries?.put(profile.id, PlayerListEntry(profile, false))
                spawnPlayer(profile)
            }
        }

        onDisable {
            deletePlayer()
        }

        onShutdown { disable() }

        listen<ConnectionEvent.Disconnect> {
            disable()
        }
    }

    private fun SafeContext.spawnPlayer(profile: GameProfile) {
        fakePlayer = OtherClientPlayerEntity(world, profile)
            .apply {
                copyFrom(player)

                playerListEntry = PlayerListEntry(profile, false)
                id = -2024 - 4 - 20
            }

        runSafeGameScheduled {
            world.addEntity(fakePlayer)
        }
    }

    private fun deletePlayer() {
        fakePlayer?.setRemoved(Entity.RemovalReason.DISCARDED)
    }
}
