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
import com.lambda.Lambda.mc
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.ConnectionEvent.Connect.Login.EncryptionResponse
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafeConcurrently
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.network.NetworkManager
import com.lambda.network.NetworkManager.updateToken
import com.lambda.network.api.v1.endpoints.login
import com.lambda.util.StringUtils.hash
import com.lambda.util.extension.isOffline
import net.minecraft.SharedConstants
import net.minecraft.client.network.AllowedAddressResolver
import net.minecraft.client.network.ClientLoginNetworkHandler
import net.minecraft.client.network.ServerAddress
import net.minecraft.network.ClientConnection
import net.minecraft.network.NetworkSide.CLIENTBOUND
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket
import net.minecraft.text.Text
import java.math.BigInteger
import kotlin.jvm.optionals.getOrElse


object Network : Module(
    name = "Network",
    description = "Lambda Authentication",
    defaultTags = setOf(ModuleTag.CLIENT),
    enabledByDefault = true,
) {
    val authServer  by setting("Auth Server", "auth.lambda-client.org")
    val apiUrl      by setting("API Server", "https://api.lambda-client.org")
    val apiVersion  by setting("API Version", ApiVersion.V1)
    val mappings    by setting("Mappings", "https://mappings.lambda-client.org")

    val gameVersion = SharedConstants.getGameVersion().name

    private var hash: String? = null

    init {
        listenUnsafeConcurrently<ClientEvent.Startup> { authenticate() }

        listenUnsafe<EncryptionResponse> { event ->
            if (event.secretKey.isDestroyed) return@listenUnsafe

            // Server id is always empty when sent by the Notchian server
            val computed = byteArrayOf()
                .hash("SHA-1", event.secretKey.encoded, event.publicKey.encoded)

            hash = BigInteger(computed).toString(16)
        }

        listenUnsafeConcurrently<ConnectionEvent.Connect.Post> {
            // FixMe: If the player have the properties but are invalid this doesn't work
            if (NetworkManager.isValid || mc.gameProfile.isOffline) return@listenUnsafeConcurrently

            // If we log in right as the client responds to the encryption request, we start
            // a race condition where the game server haven't acknowledged the packets
            // and posted to the sessionserver api
            login(mc.session.username, hash ?: return@listenUnsafeConcurrently)
                .fold(
                    onSuccess = { updateToken(it) },
                    onFailure = { LOG.warn("Unable to authenticate: $it") }
                )
        }
    }

    private fun authenticate() {
        val address = ServerAddress.parse(authServer)
        val connection = ClientConnection(CLIENTBOUND)
        val resolved = AllowedAddressResolver.DEFAULT.resolve(address)
            .map { it.inetSocketAddress }.getOrElse { return }

        ClientConnection.connect(resolved, mc.options.shouldUseNativeTransport(), connection)
            .syncUninterruptibly()

        val handler = ClientLoginNetworkHandler(connection, mc, null, null, false, null) { Text.empty() }

        connection.connect(resolved.hostName, resolved.port, handler)
        connection.send(LoginHelloC2SPacket(mc.session.username, mc.session.uuidOrNull))
    }

    enum class ApiVersion(val value: String) {
        // We can use @Deprecated("Not supported") to remove old API versions in the future
        V1("v1"),
    }
}
