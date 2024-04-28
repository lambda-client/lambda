package com.lambda.http.api.rpc.v1.models

data class Party(
    // The ID of the party.
    // It is a random string of 30 characters.
    val id: String,

    // The join secret of the party.
    // It is a random string of 100 characters.
    val joinSecret: String,

    // The leader of the party
    val leader: Player,

    // The creation date of the party.
    // example: 2021-10-10T12:00:00Z
    val creation: String,

    // The list of players in the party.
    val players: List<Player>,

    // The settings of the party
    val settings: Settings,
)
