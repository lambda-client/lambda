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

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.gson.responseObject
import com.lambda.module.modules.client.Network.apiUrl
import com.lambda.module.modules.client.Network.apiVersion
import com.lambda.network.NetworkManager
import com.lambda.network.api.v1.models.Authentication

/**
 * Links a Discord account to a session account
 *
 * Example:
 *  - token: OTk1MTU1NzcyMzYxMTQ2NDM4
 *
 * response: [Authentication] or error
 */
fun linkDiscord(discordToken: String) =
	Fuel.post("${apiUrl}/api/${apiVersion.value}/link/discord")
		.jsonBody("""{ "token": "$discordToken" }""")
		.authentication()
		.bearer(NetworkManager.accessToken)
		.responseObject<Authentication>().third
