/*
 * Copyright 2026 Lambda
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

package com.lambda.network.mojang

import com.lambda.network.LAMBDA_HTTP
import com.mojang.authlib.GameProfile
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.*

private const val BULK_URL = "https://api.mojang.com/profiles/minecraft"
private const val BULK_LIMIT = 10

private val UNDASHED =
    Regex("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})")

/**
 * Gets a game profile from a username
 *
 * Example:
 *  - name: jeb_
 */
suspend fun getProfile(name: String) = runCatching {
    LAMBDA_HTTP.get("https://api.mojang.com/users/profiles/minecraft/$name").body<GameProfile>()
}

internal class BulkProfile(val id: String, val name: String)

internal fun toGameProfile(id: String, name: String): GameProfile? =
    UNDASHED.matchEntire(id)
        ?.groupValues
        ?.drop(1)
        ?.joinToString("-")
        ?.let { GameProfile(UUID.fromString(it), name) }

suspend fun getProfiles(names: List<String>): List<GameProfile> = coroutineScope {
    val gate = Semaphore(4)
    names.chunked(BULK_LIMIT)
        .map { batch -> async { gate.withPermit { requestProfiles(batch) } } }
        .awaitAll()
        .flatten()
}

private suspend fun requestProfiles(batch: List<String>): List<GameProfile> = runCatching {
    LAMBDA_HTTP.post(BULK_URL) {
        contentType(ContentType.Application.Json)
        setBody(batch)
    }.body<List<BulkProfile>>().mapNotNull { toGameProfile(it.id, it.name) }
}.getOrDefault(emptyList())

/**
 * Gets a game profile from a [UUID]
 *
 * Example:
 *  - name: ab24f5d6-dcf1-45e4-897e-b50a7c5e7422
 */
suspend fun getProfile(uuid: UUID) = runCatching {
    LAMBDA_HTTP.get("https://api.minecraftservices.com/minecraft/profile/lookup/$uuid").body<GameProfile>()
}
