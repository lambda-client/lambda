
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

sealed class PlayerPacketEvent {
    data class Send(
        var packet: PlayerMoveC2SPacket,
    ) : ICancellable by Cancellable()

    class Post : Event
}
