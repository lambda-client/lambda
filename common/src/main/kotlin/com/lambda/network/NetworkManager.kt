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

package com.lambda.network

import com.lambda.Lambda.mc
import com.lambda.config.Configurable
import com.lambda.config.configurations.UserConfig
import com.lambda.core.Loadable
import com.lambda.network.api.v1.models.Authentication
import com.lambda.network.api.v1.models.Authentication.Data
import com.lambda.util.StringUtils.base64UrlDecode
import com.lambda.util.StringUtils.json
import com.lambda.util.collections.updatableLazy
import java.util.*

object NetworkManager : Configurable(UserConfig), Loadable {
    override val name = "network"

    var accessToken by setting("access_token", ""); private set

    val isValid: Boolean
        get() = mc.gameProfile.name == auth.value?.data?.name &&
                mc.gameProfile.id == auth.value?.data?.uuid &&
                System.currentTimeMillis() > (auth.value?.expirationDate ?: Long.MAX_VALUE)

    private val auth = updatableLazy {
        val parts = accessToken.split(".")
        if (parts.size != 3) return@updatableLazy null

        val payload = parts[1]
        val data = payload.base64UrlDecode().json<Data>()

        return@updatableLazy if (System.currentTimeMillis() < data.expirationDate) null
        else data
    }

    fun updateToken(resp: Authentication) {
        accessToken = resp.accessToken
        auth.update()
    }


    override fun load(): String {
        auth.update()

        return auth.value
            ?.let { "Logged you in as ${it.data.name} (${it.data.uuid})" }
            ?: "You are not authenticated"
    }
}
