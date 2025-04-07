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
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResultHandler
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.success
import com.lambda.module.modules.client.Network.apiUrl
import com.lambda.module.modules.client.Network.apiVersion
import com.lambda.network.api.v1.models.Cape
import java.util.UUID

/**
 * Gets the cape of the given player UUID
 *
 * Example:
 *  - id: ab24f5d6-dcf1-45e4-897e-b50a7c5e7422
 *
 * response: [Cape] or error
 */
fun getCape(uuid: UUID, success: (Cape) -> Unit, failure: (FuelError) -> Unit) =
	Fuel.get("$apiUrl/api/${apiVersion.value}/cape?id=$uuid")
		.responseObject<Cape> { _, _, result -> result.fold(success, failure) }
