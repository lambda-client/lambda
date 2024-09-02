package com.lambda.http.api.rpc.v1.models

import com.google.gson.annotations.SerializedName

data class Party(
    // The ID of the party.
    // It is a random string of 30 characters.
    @SerializedName("id")
    val id: String,

    // The join secret of the party.
    // It is a random string of 100 characters.
    @SerializedName("join_secret")
    val joinSecret: String,

    // The leader of the party
    @SerializedName("leader")
    val leader: Player,

    // The creation date of the party.
    // example: 2021-10-10T12:00:00Z
    @SerializedName("creation")
    val creation: String,

    // The list of players in the party.
    @SerializedName("players")
    val players: List<Player>,

    // The settings of the party
    @SerializedName("settings")
    val settings: Settings,
)
