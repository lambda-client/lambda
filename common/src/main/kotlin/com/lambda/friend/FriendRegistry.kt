package com.lambda.friend

import com.lambda.config.Configurable
import com.lambda.config.configurations.FriendConfig
import com.lambda.core.Loadable
import com.mojang.authlib.GameProfile

object FriendRegistry : Configurable(FriendConfig), Loadable {
    override val name = "friends"

    val friends by setting("friends", listOf<GameProfile>()) // Todo: Fix the fucking delegates

    override fun load(): String {
        return "Loaded ${friends.size} friends."
    }
}
