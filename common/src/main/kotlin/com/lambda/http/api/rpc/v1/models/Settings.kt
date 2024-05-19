package com.lambda.http.api.rpc.v1.models

import com.google.gson.annotations.SerializedName

data class Settings(
    // The maximum number of players in the party.
    // example: 10
    @SerializedName("max_players")
    val maxPlayers: Int,

    // Whether the party is public or not.
    // If false can only be joined by invite.
    // example: true
    @SerializedName("public")
    val public: Boolean,
)
