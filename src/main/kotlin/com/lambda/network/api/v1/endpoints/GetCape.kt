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

import com.lambda.network.LambdaAPI.apiUrl
import com.lambda.network.LambdaAPI.apiVersion
import com.lambda.network.LambdaHttp
import com.lambda.network.api.v1.models.Cape
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.util.*

/**
 * Gets the cape of the given player UUID
 *
 * Example:
 *  - id: ab24f5d6-dcf1-45e4-897e-b50a7c5e7422
 */
suspend fun getCape(uuid: UUID) = runCatching {
	LambdaHttp.get("$apiUrl/api/$apiVersion/cape?id=$uuid").body<Cape>()
}
