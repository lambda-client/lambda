package com.lambda.http.api.rpc.v1.models

data class Player (
    val player: MinecraftPlayer,
    val discord: DiscordUser,
)
