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

package com.lambda.module.modules.client

import com.github.kittinunf.fuel.core.FuelManager
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.ConnectionEvent.Connect.Login.EncryptionRequest
import com.lambda.event.events.ConnectionEvent.Connect.Login.EncryptionResponse
import com.lambda.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafeConcurrently
import com.lambda.network.api.v1.endpoints.login
import com.lambda.network.api.v1.models.Authentication
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication
import com.lambda.util.Communication.debug
import com.lambda.util.Communication.toast
import net.minecraft.client.network.AllowedAddressResolver
import net.minecraft.client.network.ClientLoginNetworkHandler
import net.minecraft.client.network.ServerAddress
import net.minecraft.network.ClientConnection
import net.minecraft.network.NetworkSide.CLIENTBOUND
import net.minecraft.network.encryption.NetworkEncryptionUtils
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket
import net.minecraft.text.Text
import java.math.BigInteger
import java.sql.Time

object Network : Module(
    name = "Network",
    description = "...",
    defaultTags = setOf(ModuleTag.CLIENT),
    enabledByDefault = true,
) {
    var authServer by setting("Auth Server", "auth.lambda-client.org")
    var apiUrl: String by setting("API Server", "https://api.lambda-client.org")
    var apiVersion by setting("API Version", ApiVersion.V1)

    var apiAuth: Authentication? = null; private set // TODO: Cache
    val accessToken: String
        get() = apiAuth?.accessToken ?: ""

    private lateinit var serverId: String
    private lateinit var hash: String

    init {
        listenUnsafe<EncryptionRequest> { serverId = it.serverId }

        listenUnsafe<EncryptionResponse> { event ->
            if (event.secretKey.isDestroyed) return@listenUnsafe

            hash = BigInteger(
                NetworkEncryptionUtils.computeServerId(serverId, event.publicKey, event.secretKey)
            ).toString(16)
        }

        listenOnceUnsafe<ConnectionEvent.Connect.Post> {
            // If we log in right as the client responds to the encryption request, we start
            // a race condition where the game server haven't acknowledged the packets
            // and posted to the sessionserver api
            val (authResponse, error) = login(mc.session.username, hash)
            if (error != null) {
                LOG.debug("Unable to authenticate with the API: {}", error.errorData)
                return@listenOnceUnsafe false
            }

            apiAuth = authResponse

            // Destroy the listener
            true
        }

        listenUnsafeConcurrently<ClientEvent.Startup> {
            // ToDo: Check if player is online before connecting
            val addddd = ServerAddress.parse(authServer)
            val connection = ClientConnection(CLIENTBOUND)
            val addr = AllowedAddressResolver.DEFAULT.resolve(addddd)
                .map { it.inetSocketAddress }.get()

            ClientConnection.connect(addr, mc.options.shouldUseNativeTransport(), connection)
                .syncUninterruptibly()

            val handler = ClientLoginNetworkHandler(connection, mc, null, null, false, null) { Text.empty() }

            connection.connect(addr.hostName, addr.port, handler)
            connection.send(LoginHelloC2SPacket(mc.session.username, mc.session.uuidOrNull))
        }
    }

    internal fun updateToken(auth: Authentication) { apiAuth = auth }

    enum class ApiVersion(val value: String) {
        // We can use @Deprecated("Not supported") to remove old API versions in the future
        V1("v1"),
    }
}
