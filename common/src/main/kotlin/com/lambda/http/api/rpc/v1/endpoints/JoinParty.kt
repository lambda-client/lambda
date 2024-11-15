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

import com.lambda.http.Method
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.http.request

fun joinParty(
    endpoint: String,
    version: String,
    accessToken: String,

    // The ID of the party.
    // example: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
    partyId: String,
) =
    request("$endpoint/api/$version/party/join") {
        method(Method.PUT)

        parameters(
            mapOf(
                "id" to partyId,
            )
        )

        headers(
            mapOf("Authorization" to "Bearer $accessToken")
        )
    }.json<Party>()
