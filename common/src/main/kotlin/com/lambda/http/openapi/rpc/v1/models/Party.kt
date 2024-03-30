package com.lambda.http.openapi.rpc.v1.models

data class Party(
    val id: String,
    val creation: String,
    val players: List<Player>,
)
