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

import com.lambda.Lambda.gson
import com.lambda.Lambda.mc
import com.lambda.config.Configurable
import com.lambda.config.configurations.UserConfig
import com.lambda.core.Loadable
import com.lambda.network.api.v1.models.Authentication
import com.lambda.network.api.v1.models.Authentication.Data
import com.lambda.util.reflections.getResources
import java.io.File
import java.util.*

object NetworkManager : Configurable(UserConfig), Loadable {
    override val name = "network"

    var accessToken by setting("authentication", ""); private set

    val isDiscordLinked: Boolean
        get() = deserialized?.data?.discordId != null

    /**
     * Returns whether the auth has expired
     */
    val isExpired: Boolean
        get() = (deserialized?.expirationDate ?: 0) < System.currentTimeMillis()

    /**
     * Returns whether the auth token is invalid or not
     */
    val isValid: Boolean
        get() = mc.gameProfile.name == deserialized?.data?.name &&
                mc.gameProfile.id == deserialized?.data?.uuid &&
                !isExpired

    private var deserialized: Data? = null

    // ToDo: Fetch remote file instead of checking local files
    val capes = getResources(".*.png")
        .filter { it.contains("capes") } // filterByInput hangs the program
        .map { File(it).nameWithoutExtension }

    fun updateToken(resp: Authentication) {
        accessToken = resp.accessToken
        decodeAuth(accessToken)
    }

    private fun decodeAuth(token: String) {
        val payload = token.split(".").getOrNull(1) ?: return
        deserialized = gson.fromJson(String(Base64.getUrlDecoder().decode(payload)), Data::class.java)
    }

    override fun load(): String {
        decodeAuth(accessToken)

        // ToDo: Re-authenticate every 24 hours

        return "Loaded ${capes.size} capes"
    }
}
