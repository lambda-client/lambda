/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.http.api.rpc.v1.models

import com.google.gson.annotations.SerializedName
import java.util.*

data class Party(
    // The ID of the party.
    // It is a random string of 30 characters.
    @SerializedName("id")
    val id: UUID,

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
