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

package com.lambda.network.mojang

import com.lambda.network.LambdaHttp
import com.mojang.authlib.GameProfile
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.util.*

/**
 * Gets a game profile from a username
 *
 * Example:
 *  - name: jeb_
 *
 * @return result of [GameProfile]
 */
suspend fun getProfile(name: String) = runCatching {
    LambdaHttp.get("https://api.mojang.com/users/profiles/minecraft/$name").body<GameProfile>()
}

/**
 * Gets a game profile from a [UUID]
 *
 * Example:
 *  - name: ab24f5d6-dcf1-45e4-897e-b50a7c5e7422
 *
 * @return result of [GameProfile]
 */
suspend fun getProfile(uuid: UUID) = runCatching {
    LambdaHttp.get("https://api.minecraftservices.com/minecraft/profile/lookup/$uuid").body<GameProfile>()
}
