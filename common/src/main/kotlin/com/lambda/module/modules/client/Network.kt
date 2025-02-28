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

import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.ConnectionEvent.Connect.Login.EncryptionRequest
import com.lambda.event.events.ConnectionEvent.Connect.Login.EncryptionResponse
import com.lambda.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafeConcurrently
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.network.api.v1.endpoints.login
import com.lambda.network.api.v1.models.Authentication
import com.lambda.network.api.v1.models.Authentication.Data
import com.lambda.util.extension.isOffline
import net.minecraft.client.network.AllowedAddressResolver
import net.minecraft.client.network.ClientLoginNetworkHandler
import net.minecraft.client.network.ServerAddress
import net.minecraft.network.ClientConnection
import net.minecraft.network.NetworkSide.CLIENTBOUND
import net.minecraft.network.encryption.NetworkEncryptionUtils
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket
import net.minecraft.text.Text
import java.math.BigInteger
import java.util.*


object Network : Module(
    name = "Network",
    description = "Lambda Authentication",
    defaultTags = setOf(ModuleTag.CLIENT),
    enabledByDefault = true,
) {
    val authServer  by setting("Auth Server", "auth.lambda-client.org")
    val apiUrl      by setting("API Server", "https://api.lambda-client.org")
    val apiVersion  by setting("API Version", ApiVersion.V1)

    private var auth: Authentication? = null
    private var deserialized: Data? = null
    val accessToken: String
        get() = auth?.accessToken ?: ""

    val SafeContext.isDiscordLinked: Boolean
        get() = deserialized?.data?.discordId != null

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
            if (mc.gameProfile.isOffline) return@listenOnceUnsafe true // ToDo: If the player have the properties but are invalid this doesn't work

            // If we log in right as the client responds to the encryption request, we start
            // a race condition where the game server haven't acknowledged the packets
            // and posted to the sessionserver api
            val (resp, error) = login(mc.session.username, hash)
            if (error != null) {
                LOG.debug("Unable to authenticate: ${error.message}")
                return@listenOnceUnsafe false
            }

            updateToken(resp)

            true
        }

        listenUnsafeConcurrently<ClientEvent.Startup> { authenticate() }
    }

    private fun authenticate() {
        val address = ServerAddress.parse(authServer)
        val connection = ClientConnection(CLIENTBOUND)
        val resolved = AllowedAddressResolver.DEFAULT.resolve(address)
            .map { it.inetSocketAddress }.get()

        ClientConnection.connect(resolved, mc.options.shouldUseNativeTransport(), connection)
            .syncUninterruptibly()

        val handler = ClientLoginNetworkHandler(connection, mc, null, null, false, null) { Text.empty() }

        connection.connect(resolved.hostName, resolved.port, handler)
        connection.send(LoginHelloC2SPacket(mc.session.username, mc.session.uuidOrNull))
    }

    internal fun updateToken(resp: Authentication?) {
        auth = resp
        deserialized = gson.fromJson(String(Base64.getUrlDecoder().decode(accessToken.split(".")[1])), Data::class.java)
    }

    enum class ApiVersion(val value: String) {
        // We can use @Deprecated("Not supported") to remove old API versions in the future
        V1("v1"),
    }
}
