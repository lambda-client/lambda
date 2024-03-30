package com.lambda.event.events

import com.lambda.event.Event
import com.mojang.authlib.GameProfile
import net.minecraft.network.listener.PacketListener
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent
import net.minecraft.text.Text
import java.security.PublicKey
import java.util.UUID
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
        ) : ConnectionEvent()

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
             * Event representing a hello message during login.
             * @property name The name associated with the login.
             * @property uuid The UUID associated with the login.
             */
            class Hello(
                val name: String,
                val uuid: UUID,
            ) : ConnectionEvent()

            /**
             * Event representing the exchange of cryptographic keys during login.
             * @property secretKey The secret key exchanged during login.
             * @property publicKey The public key exchanged during login.
             * @property nonce The nonce associated with the login.
             *
             * The secret key MUST ABSOLUTELY be destroyed after use to prevent memory leaks and security vulnerabilities.
             * This can be done by calling the `destroy()` method on the secret key object.
             * We are NOT responsible for any security incidents that may occur due to improper handling of cryptographic keys.
             * It cannot be used for stealing accounts, but you may still want to keep it secret.
             */
            class Key(
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
     * Event representing a disconnection.
     * @property reason The reason for disconnection.
     */
    class Disconnect(val reason: Text) : ConnectionEvent()
}
