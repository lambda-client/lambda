/*
 * Copyright 2025 Lambda
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

package com.lambda.network.api.v1.endpoints

import com.lambda.module.modules.client.Network.apiUrl
import com.lambda.module.modules.client.Network.apiVersion
import com.lambda.network.LambdaHttp
import com.lambda.network.NetworkManager
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Sets the currently authenticated player's cape
 *
 * Example:
 *  - id: galaxy
 *
 * response: [Unit] or error
 */
suspend fun setCape(id: String) = runCatching {
	LambdaHttp.put("$apiUrl/api/${apiVersion.value}/cape?id=$id") {
		bearerAuth(NetworkManager.accessToken)
		contentType(ContentType.Application.Json)
	}
}
