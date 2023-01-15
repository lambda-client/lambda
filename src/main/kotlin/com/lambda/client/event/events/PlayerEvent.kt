package com.lambda.client.event.events

import com.lambda.client.commons.extension.next
import com.lambda.client.event.*
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.util.math.Vec2f
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemFood
import net.minecraft.util.math.Vec3d

/**
 * TargetEvent is used for events related to the player
 */
open class PlayerEvent : Event {
    class OnDeath(val entity: EntityLivingBase) : TargetEvent()

    class OnAttacked(val entity: EntityLivingBase) : TargetEvent()

    class Attack(val target: EntityLivingBase) : TargetEvent(), ICancellable by Cancellable() {
        var swingHand = true
    }

    class OnEatStart(val target: EntityLivingBase, val food: ItemFood) : TargetEvent()

    class OnEatFinish(val target: EntityLivingBase) : TargetEvent()

    class Travel : Event, ICancellable by Cancellable(), ProfilerEvent {
        override val profilerName: String = "kbPlayerTravel"
    }

    class Move(
        private val player: EntityPlayerSP
    ) : Event, Cancellable() {
        private val prevX = player.motionX
        private val prevY = player.motionY
        private val prevZ = player.motionZ

        val isModified: Boolean
            get() = player.motionX != prevX
                || player.motionY != prevY
                || player.motionZ != prevZ

        var x: Double
            get() = if (cancelled) 0.0 else player.motionX
            set(value) {
                if (!cancelled) player.motionX = value
            }

        var y: Double
            get() = if (cancelled) 0.0 else player.motionY
            set(value) {
                if (!cancelled) player.motionY = value
            }

        var z: Double
            get() = if (cancelled) 0.0 else player.motionZ
            set(value) {
                if (!cancelled) player.motionZ = value
            }
    }

    class UpdateWalking private constructor(
        moving: Boolean,
        rotating: Boolean,
        position: Vec3d,
        rotation: Vec2f,
        override val phase: Phase
    ) : Event, IMultiPhase<UpdateWalking>, Cancellable() {

        var position = position; private set
        var rotation = rotation; private set

        var moving = moving
            @JvmName("isMoving") get
            private set

        var rotating = rotating
            @JvmName("isRotating") get
            private set

        var cancelAll = false; private set

        constructor(moving: Boolean, rotating: Boolean, position: Vec3d, rotation: Vec2f) : this(moving, rotating, position, rotation, Phase.PRE)

        override fun nextPhase(): UpdateWalking {
            return UpdateWalking(moving, rotating, position, rotation, phase.next())
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
}