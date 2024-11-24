/*
 * Copyright 2024 Lambda
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

/**
 * Sealed class representing connection events.
 */
sealed class ConnectionEvent : Event {
    /**
     * Sealed class representing various stages of connection establishment.
     */
    sealed class Connect {
        /**
         * Event representing a pre-connection attempt.
         * @property address The address of the connection attempt.
         * @property port The port of the connection attempt.
         * @property listener The packet listener associated with the connection.
         * @property intent The connection intent.
         */
        class Pre(
            val address: String,
            val port: Int,
            val listener: PacketListener,
            val intent: ConnectionIntent,
        ) : ConnectionEvent(), ICancellable by Cancellable()

        /**
         * Event representing a handshake during connection.
         * @property protocolVersion The protocol version of the connection.
         * @property address The address of the connection attempt.
         * @property port The port of the connection attempt.
         * @property intent The connection intent.
         */
        class Handshake(
            val protocolVersion: Int,
            val address: String,
            val port: Int,
            val intent: ConnectionIntent,
        ) : ConnectionEvent()

        /**
         * Sealed class representing login-related connection events.
         */
        sealed class Login : ConnectionEvent() {
            /**
             * @see <a href="https://wiki.vg/index.php?title=Protocol&oldid=19208#Encryption_Request">Encryption Request</a>
             */
            class EncryptionRequest(
                val serverId: String,
                val publicKey: PublicKey,
                val nonce: ByteArray,
            ) : ConnectionEvent()

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
             * @see <a href="https://wiki.vg/index.php?title=Protocol&oldid=19208#Encryption_Response">Encryption Response</a>
             */
            class EncryptionResponse(
                val secretKey: SecretKey,
                val publicKey: PublicKey,
                val nonce: ByteArray,
            ) : ConnectionEvent()
        }

        /**
         * Event representing post-connection actions.
         * @property profile The game profile associated with the connection.
         */
        class Post(
            val profile: GameProfile,
        ) : ConnectionEvent()
    }

    /**
     * @property reason The reason for disconnection.
     */
    class Disconnect(val reason: Text) : ConnectionEvent()
}
