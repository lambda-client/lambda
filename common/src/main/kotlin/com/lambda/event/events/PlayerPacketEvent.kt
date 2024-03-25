package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.interaction.rotation.Rotation
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d

abstract class PlayerPacketEvent : Event {
    class Pre(
        var position: Vec3d,
        var rotation: Rotation,
        var onGround: Boolean,
        var isSprinting: Boolean,
        var isSneaking: Boolean,
    ) : PlayerPacketEvent(), ICancellable by Cancellable()

    class Post(
        val packet: PlayerMoveC2SPacket,
    ) : PlayerPacketEvent(), ICancellable by Cancellable()
}