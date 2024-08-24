package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeConcurrent
import com.lambda.util.Communication.warn
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
                var profile = GameProfile(UUID(0, 0), playerName)

                profile =
                    request("https://api.mojang.com/users/profiles/minecraft/$playerName") {
                        method(Method.GET)
                    }.json<GameProfile>()
                        .data ?: profile

                profile = mc.sessionService.fetchProfile(profile.id, true)?.profile ?: profile

                if (mc.networkHandler?.playerListEntries?.get(profile.id) != null) {
                    warn("A player with the name $playerName is already in the world.")
                    return@runSafeConcurrent
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
