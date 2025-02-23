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

import com.lambda.Lambda
import com.lambda.module.modules.client.Network.accessToken
import com.lambda.module.modules.client.Network.apiUrl
import com.lambda.module.modules.client.Network.apiVersion
import com.lambda.network.api.v1.models.Party
import com.lambda.threading.runConcurrent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Waiting for https://github.com/kittinunf/fuel/issues/989 before changing this
fun partyUpdates(block: (Party?) -> Unit) {
	runConcurrent {
		HttpClient.newHttpClient().sendAsync(
			HttpRequest.newBuilder()
				.uri(URI.create("${apiUrl}/api/${apiVersion.value}/party/listen"))
				.header("Accept", "text/event-stream")
				.header("Authorization", "Bearer $accessToken")
				.build(),
			HttpResponse.BodyHandlers.ofLines()
		).thenAccept { response ->
			response.body().forEach {
				if (!it.startsWith("data:")) return@forEach

				val data = it.substring(5).trim()
				val party = runCatching { Lambda.gson.fromJson(data, Party::class.java) }.getOrNull()

				block(party)
			}
		}.join()
	}
}
