package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import java.util.*

object FakePlayer : Module(
    name = "FakePlayer",
    description = "Spawns a fake player",
    tag = ModuleTag.MISC
) {
    private val playerName by setting("Name", "Steve")

    private val uuid = UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77")
    private var fakePlayer: OtherClientPlayerEntity? = null

    init {
        onEnable {
            fakePlayer = OtherClientPlayerEntity(world, GameProfile(uuid, playerName))
                .apply(player::copyFrom)
                .apply(world::addEntity)
                .apply { id = -2024-4-20 }
        }

        onDisable {
            fakePlayer?.setRemoved(Entity.RemovalReason.DISCARDED)
        }
    }
}
