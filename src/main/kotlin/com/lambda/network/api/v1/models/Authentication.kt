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

package com.lambda.network.api.v1.models

import com.google.gson.annotations.SerializedName

data class Authentication(
	@SerializedName("access_token")
	val accessToken: String,

	@SerializedName("expires_in")
	val expiresIn: Long,

	@SerializedName("token_type")
	val tokenType: String,
) {
	data class Data(
		@SerializedName("nbf")
		val notBefore: Long,

		@SerializedName("iat")
		val issuedAt: Long,

		@SerializedName("exp")
		val expirationDate: Long,

		@SerializedName("data")
		val data: Player,
	)
}
