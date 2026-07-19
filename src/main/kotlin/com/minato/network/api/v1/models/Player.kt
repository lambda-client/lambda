
package com.minato.network.api.v1.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class Player(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("id")
    val uuid: UUID,

    @JsonProperty("discord_id")
    val discordId: String,

    // Whether the player is verified or not
    @JsonProperty("unsafe")
    val unsafe: Boolean,
)
