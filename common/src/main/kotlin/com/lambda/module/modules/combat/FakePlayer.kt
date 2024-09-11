package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeConcurrent
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

    init {
        onEnable {
            // Avoid multiple api requests
            if (fakePlayer?.gameProfile?.name == playerName)
                return@onEnable spawnPlayer(fakePlayer!!.gameProfile)

            runSafeConcurrent {
                val uuid =
                    request("https://api.mojang.com/users/profiles/minecraft/$playerName") {
                        method(Method.GET)
                    }.json<GameProfile>().data?.id ?: UUID(0, 0)

                val fetchedProperties = mc.sessionService.fetchProfile(uuid, true)?.profile?.properties

                val profile = GameProfile(UUID(0, 0), playerName).apply {
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
    }

    private fun SafeContext.spawnPlayer(profile: GameProfile) {
        fakePlayer = OtherClientPlayerEntity(world, profile)
            .apply {
                copyFrom(player)

                playerListEntry = PlayerListEntry(profile, false)
                id = -2024 - 4 - 20
            }

        world.addEntity(fakePlayer)
    }

    private fun deletePlayer() {
        fakePlayer?.setRemoved(Entity.RemovalReason.DISCARDED)
    }
}
