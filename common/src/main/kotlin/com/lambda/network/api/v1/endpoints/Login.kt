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

package com.lambda.network.api.v1.endpoints

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import com.lambda.network.api.v1.models.Authentication

fun login(
	// The player's Discord token.
	// example: OTk1MTU1NzcyMzYxMTQ2NDM4
	discordToken: String,

	// The player's username.
	// example: "Notch"
	username: String,

	// The player's Mojang session hash.
	// example: 069a79f444e94726a5befca90e38aaf5
	hash: String,
) =
	Fuel.post("/login", listOf("token" to discordToken, "username" to username, "hash" to hash))
		.responseObject<Authentication>().third
