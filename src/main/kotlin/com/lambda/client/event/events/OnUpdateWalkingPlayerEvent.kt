package com.lambda.client.event.events

import com.lambda.client.commons.extension.next
import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.IMultiPhase
import com.lambda.client.event.Phase
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.util.math.Vec2f
import net.minecraft.util.math.Vec3d

class OnUpdateWalkingPlayerEvent private constructor(
    moving: Boolean,
    rotating: Boolean,
    position: Vec3d,
    rotation: Vec2f,
    override val phase: Phase
) : Event, IMultiPhase<OnUpdateWalkingPlayerEvent>, Cancellable() {

    var position = position; private set
    var rotation = rotation; private set

    var moving = moving
        @JvmName("isMoving") get

    var rotating = rotating
        @JvmName("isRotating") get
        private set

    var cancelAll = false; private set

    constructor(moving: Boolean, rotating: Boolean, position: Vec3d, rotation: Vec2f) : this(moving, rotating, position, rotation, Phase.PRE)

    override fun nextPhase(): OnUpdateWalkingPlayerEvent {
        return OnUpdateWalkingPlayerEvent(moving, rotating, position, rotation, phase.next())
    }

    fun apply(packet: PlayerPacketManager.Packet) {
        cancel()

        packet.moving?.let { moving ->
            if (moving) packet.position?.let { this.position = it }
            this.moving = moving
        }

        packet.rotating?.let { rotating ->
            if (rotating) packet.rotation?.let { this.rotation = it }
            this.rotating = rotating
        }

        this.cancelAll = packet.cancelAll
    }

}