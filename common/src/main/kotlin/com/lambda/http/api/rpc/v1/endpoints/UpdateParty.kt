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

package com.lambda.http.api.rpc.v1.endpoints

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import com.lambda.http.api.rpc.v1.models.Party

fun editParty(
    endpoint: String,
    version: String,
    accessToken: String,

    // The maximum number of players in the party.
    // example: 10
    maxPlayers: Int = 10,

    // Whether the party is public or not.
    // If false can only be joined by invite.
    // example: true
    // public: Boolean = true,
) =
    Fuel.patch("$endpoint/api/$version/party/edit", listOf("max_players" to maxPlayers))
        .responseObject<Party>().third
