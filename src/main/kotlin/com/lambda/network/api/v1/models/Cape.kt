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
import com.lambda.network.LambdaAPI
import java.util.*

class Cape(
	@SerializedName("uuid")
	val uuid: UUID,

	@SerializedName("type")
	val id: String,
) {
	val url: String
		get() = "${LambdaAPI.capes}/$id.png"

	override fun toString() = "Cape(uuid=$uuid, id=$id, url=$url)"
}
