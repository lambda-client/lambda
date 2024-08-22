package com.lambda.module.modules.combat

import com.google.gson.annotations.SerializedName
import com.lambda.context.SafeContext
import com.lambda.http.Method
import com.lambda.http.request
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeConcurrent
import com.lambda.threading.runSafeGameConcurrent
import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.authlib.properties.Property
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.texture.PlayerSkinProvider
import net.minecraft.client.world.ClientWorld
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
                val profile =
                    request("https://api.mojang.com/users/profiles/minecraft/$playerName") {
                        method(Method.GET)
                    }.json<GameProfile>()
                        .data ?: return@runSafeConcurrent spawnPlayer(GameProfile(UUID.randomUUID(), playerName))

                val properties =
                    request("https://sessionserver.mojang.com/session/minecraft/profile/${profile.id}") {
                        method(Method.GET)
                    }.json<PlayerSkinProviderKey>()
                        .data ?: return@runSafeConcurrent spawnPlayer(profile)

                val textureProperty = properties.textureProperty ?: return@runSafeConcurrent spawnPlayer(profile)
                val textures = mc.sessionService.unpackTextures(textureProperty)

                // Fetch and cache the skin textures
                mc.skinProvider.fetchSkinTextures(profile.id, textures)

                // Hack the game profile to include the skin textures
                profile.properties.put("textures", textureProperty)

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

    private fun SafeContext.deletePlayer() {
        fakePlayer?.setRemoved(Entity.RemovalReason.DISCARDED)
    }

    private data class PlayerSkinProviderKey(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("properties") val properties: List<Property>
    ) {
        val textureProperty: Property?
            get() = properties.firstOrNull { it.name == "textures" }
    }
}
