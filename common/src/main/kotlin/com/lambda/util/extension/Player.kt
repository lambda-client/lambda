package com.lambda.util.extension

import com.mojang.authlib.GameProfile
import net.minecraft.entity.player.PlayerEntity

val PlayerEntity.isOffline
    get() = gameProfile.isOffline

val GameProfile.isOffline
    get() = properties.isEmpty
