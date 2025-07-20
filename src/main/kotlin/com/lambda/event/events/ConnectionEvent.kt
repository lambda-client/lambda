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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.mojang.authlib.GameProfile
import net.minecraft.network.listener.PacketListener
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent
import net.minecraft.text.Text
import java.security.PublicKey
import javax.crypto.SecretKey

sealed class ConnectionEvent {
    sealed class Connect {
        /**
         * Event representing a pre-connection attempt
         *
         * @property address The address of the connection attempt
         * @property port The port of the connection attempt
         * @property listener The packet listener associated with the connection
         * @property intent The connection intent
         */
        data class Pre(
            val address: String,
            val port: Int,
            val listener: PacketListener,
            val intent: ConnectionIntent,
        ) : ICancellable by Cancellable()

        /**
         * Event representing a handshake during connection
         *
         * @property protocolVersion The protocol version of the connection
         * @property address The address of the connection attempt
         * @property port The port of the connection attempt
         * @property intent The connection intent
         *
         * @see <a href="https://wiki.vg/Server_List_Ping#Handshake">Server_List_Ping#Handshake</a>
         */
        data class Handshake(
            val protocolVersion: Int,
            val address: String,
            val port: Int,
            val intent: ConnectionIntent,
        ) : Event

        sealed class Login {
            /**
             * This event is not received by the client, it is sent from the client
             * to the server, this event simply intercepts the outbound packet
             *
             * @see <a href="https://wiki.vg/index.php?title=Protocol#Encryption_Request">Protocol#Encryption_Request</a>
             */
            class EncryptionRequest(
                val serverId: String,
                val publicKey: PublicKey,
                val nonce: ByteArray,
            ) : Event

            /**
             * Event representing the exchange of cryptographic keys during login
             * from the client to the server
             *
             * Note that this event won't be posted if the server is in offline mode
             * because the player doesn't fetch the server's public key
             *
             * The secret key must be destroyed if stored for long periods
             * This can be done by calling the `destroy()` method on the secret key object
             * We are not responsible for any incidents that may occur due to improper handling of cryptographic keys
             *
             * @see <a href="https://wiki.vg/index.php?title=Protocol#Encryption_Response">Protocol#Encryption_Response</a>
             */
            class EncryptionResponse(
                val secretKey: SecretKey,
                val publicKey: PublicKey,
                val nonce: ByteArray,
            ) : Event
        }

        /**
         * Triggered upon successful connection process end
         */
        data class Post(
            val profile: GameProfile,
        ) : Event
    }

    /**
     * Triggered upon connection failure
     */
    data class Disconnect(val reason: Text) : Event
}
