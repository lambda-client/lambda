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
import com.github.kittinunf.fuel.core.requests.CancellableRequest
import com.lambda.module.modules.client.Network.apiUrl
import com.lambda.module.modules.client.Network.apiVersion
import net.minecraft.SharedConstants

/**
 * Gets the Minecraft mappings for dynamic remapping
 *
 * Example:
 *  - version: 765
 *
 * response: File or error
 */
fun getMappings(
    version: String = SharedConstants.getGameVersion().name,
    success: (String) -> Unit,
    failure: (FuelError) -> Unit
) = Fuel.get("$apiUrl/api/${apiVersion.value}/mappings?version=$version")
        .responseString { _, _, result -> result.fold(success, failure) }
