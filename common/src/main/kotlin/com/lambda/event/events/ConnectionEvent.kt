package com.lambda.event.events

import com.lambda.event.Event
import com.mojang.authlib.GameProfile
import net.minecraft.network.listener.PacketListener
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent
import net.minecraft.text.Text
import java.security.PublicKey
import java.util.UUID
import javax.crypto.SecretKey

sealed class ConnectionEvent : Event {
    sealed class Connect {
        class Pre(
            val address: String,
            val port: Int,
            val listener: PacketListener,
            val intent: ConnectionIntent,
        ) : ConnectionEvent()

        class Handshake(
            val protocolVersion: Int,
            val address: String,
            val port: Int,
            val intent: ConnectionIntent,
        ) : ConnectionEvent()

        sealed class Login : ConnectionEvent() {
            class Hello(
                val name: String,
                val uuid: UUID,
            ) : ConnectionEvent()

            class Key(
                val secretKey: SecretKey,
                val publicKey: PublicKey,
                val nonce: ByteArray,
            ) : ConnectionEvent()
        }

        class Post(
            val profile: GameProfile,
        ) : ConnectionEvent()
    }

    class Disconnect(val reason: Text) : ConnectionEvent()
}
