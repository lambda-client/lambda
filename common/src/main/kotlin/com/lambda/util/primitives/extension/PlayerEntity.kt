package com.lambda.util.primitives.extension

import com.mojang.authlib.GameProfile
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Uuids

val PlayerEntity.isOffline
    get() = gameProfile.isOffline

val GameProfile.isOffline
    get() = Uuids.getOfflinePlayerUuid(name) == id
