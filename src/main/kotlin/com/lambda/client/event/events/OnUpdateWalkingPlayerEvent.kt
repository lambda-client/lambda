package com.lambda.client.event.events

import com.lambda.client.commons.extension.next
import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.IMultiPhase
import com.lambda.client.event.Phase
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.util.math.Vec2f
import net.minecraft.util.math.Vec3d

class OnUpdateWalkingPlayerEvent constructor(
    moving: Boolean,
    rotating: Boolean,
    var position: Vec3d,
    var rotation: Vec2f,
    override var phase: Phase
) : Event, IMultiPhase<OnUpdateWalkingPlayerEvent>, Cancellable() {

    val posInitial = position
    val rotInitial = rotation

    var moving = moving
        @JvmName("isMoving") get

    var rotating = rotating
        @JvmName("isRotating") get
        private set

    var cancelAll = false; private set

    constructor(moving: Boolean, rotating: Boolean, position: Vec3d, rotation: Vec2f) : this(moving, rotating, position, rotation, Phase.PRE)
    constructor() : this(false, false, Vec3d.ZERO, Vec2f.ZERO)

    override fun nextPhase(): OnUpdateWalkingPlayerEvent {
        phase = phase.next()
        return this
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