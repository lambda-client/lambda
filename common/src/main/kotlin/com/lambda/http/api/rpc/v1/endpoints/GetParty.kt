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

fun createParty(
    endpoint: String,
    version: String,
    accessToken: String,
) =
    request("$endpoint/api/$version/party") {
        method(Method.POST)

        headers(
            mapOf("Authorization" to "Bearer $accessToken")
        )
    }.json<Party>()
