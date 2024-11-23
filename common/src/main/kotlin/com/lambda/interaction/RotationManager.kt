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

package com.lambda.interaction

import com.lambda.Lambda.mc
import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.angleDifference
import com.lambda.interaction.rotation.Rotation.Companion.fixSensitivity
import com.lambda.interaction.rotation.Rotation.Companion.slerp
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.modules.client.Baritone
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.math.lerp
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.extension.partialTicks
import com.lambda.util.extension.rotation
import net.minecraft.client.input.Input
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import kotlin.math.*

object RotationManager : Loadable {
    var currentRotation = Rotation.ZERO
    var prevRotation = Rotation.ZERO

    var currentContext: RotationContext? = null

    private var keepTicks = 0
    private var pauseTicks = 0

    fun Any.requestRotation(
        priority: Int = 0,
        alwaysListen: Boolean = false,
        onUpdate: SafeContext.(lastContext: RotationContext?) -> RotationContext?,
        onReceive: SafeContext.() -> Unit = {}
    ) {
        var lastCtx: RotationContext? = null

        this.listener<RotationEvent.Update>(priority, alwaysListen) { event ->
            val rotationContext = onUpdate(event.context)

            rotationContext?.let {
                event.context = it
            }

            lastCtx = rotationContext
        }

        this.listener<RotationEvent.Post> { event ->
            if (event.context == lastCtx && event.context.isValid) {
                onReceive()
            }
        }
    }

    @JvmStatic
    fun update() = runSafe {
        RotationEvent.Update(BaritoneProcessor.poolContext()).post {
            rotate(context)

            currentContext?.let {
                RotationEvent.Post(it).post()
            }
        }
    }

    init {
        listener<PacketEvent.Send.Post> { event ->
            val packet = event.packet
            if (packet !is PlayerPositionLookS2CPacket) return@listener

            runGameScheduled {
                // TODO: wtf happened here ?
                // reset(Rotation(packet.yaw, packet.pitch))
            }
        }

        unsafeListener<ConnectionEvent.Disconnect> {
            reset(Rotation.ZERO)
        }
    }

    private fun rotate(newContext: RotationContext?) = runSafe {
        prevRotation = currentRotation

        keepTicks--
        pauseTicks--

        currentContext?.let { current ->
            if (keepTicks + current.config.resetTicks < 0 || pauseTicks >= 0) {
                currentContext = null
            }
        }

        newContext?.let { request ->
            currentContext = request
            keepTicks = request.config.keepTicks
        }

        currentRotation = currentContext?.let { context ->
            val rotationTo = if (keepTicks >= 0) context.rotation else player.rotation

            var speedMultiplier = (context.config as? RotationSettings)?.speedMultiplier ?: 1.0
            if (keepTicks < 0) speedMultiplier = 1.0

            val turnSpeed = context.config.turnSpeed * speedMultiplier

            currentRotation
                .slerp(rotationTo, turnSpeed)
                .fixSensitivity(prevRotation)
                .apply {
                    if (context.config.rotationMode != RotationMode.LOCK) return@apply
                    player.yaw = this.yawF
                    player.pitch = this.pitchF
                }
        } ?: player.rotation
    }

    private fun reset(rotation: Rotation) {
        prevRotation = rotation
        currentRotation = rotation

        currentContext = null
        pauseTicks = 3
    }

    private val smoothRotation
        get() =
            lerp(mc.partialTicks, prevRotation, currentRotation)

    @JvmStatic
    val lockRotation
        get() =
            if (currentContext?.config?.rotationMode == RotationMode.LOCK) smoothRotation else null

    @JvmStatic
    val renderYaw
        get() =
            if (currentContext?.config == null) null else smoothRotation.yaw.toFloat()

    @JvmStatic
    val renderPitch
        get() =
            if (currentContext?.config == null) null else smoothRotation.pitch.toFloat()

    @JvmStatic
    val handYaw
        get() =
            if (currentContext?.config?.rotationMode == RotationMode.LOCK) currentRotation.yaw.toFloat() else null

    @JvmStatic
    val handPitch
        get() =
            if (currentContext?.config?.rotationMode == RotationMode.LOCK) currentRotation.pitch.toFloat() else null

    @JvmStatic
    val movementYaw: Float?
        get() {
            if (currentContext?.config?.rotationMode == RotationMode.SILENT) return null
            return currentRotation.yaw.toFloat()
        }

    @JvmStatic
    val movementPitch: Float?
        get() {
            if (currentContext?.config?.rotationMode == RotationMode.SILENT) return null
            return currentRotation.pitch.toFloat()
        }

    @JvmStatic
    fun getRotationForVector(deltaTime: Double): Vec2d? {
        if (currentContext?.config?.rotationMode == RotationMode.SILENT) return null

        val rot = lerp(deltaTime, prevRotation, currentRotation)
        return Vec2d(rot.yaw, rot.pitch)
    }

    object BaritoneProcessor {
        private var baritoneContext: RotationContext? = null

        fun poolContext(): RotationContext? {
            val ctx = baritoneContext
            baritoneContext = null
            return ctx
        }

        private val movementYawList = arrayOf(
            0.0, 45.0,
            90.0, 135.0,
            180.0, 225.0,
            270.0, 315.0,
        )

        @JvmStatic
        fun handleBaritoneRotation(yaw: Float, pitch: Float) {
            baritoneContext = RotationContext(Rotation(yaw, pitch), Baritone.rotation.apply {
                if (rotationMode != RotationMode.SILENT) return@apply
                rotationMode = RotationMode.SYNC
            })
        }

        @JvmStatic
        fun processPlayerMovement(input: Input, slowDown: Boolean, slowDownFactor: Float) = runSafe {
            // The yaw relative to which the movement was constructed
            val baritoneYaw = baritoneContext?.rotation?.yaw
            val strafeEvent = RotationEvent.StrafeInput(baritoneYaw ?: player.yaw.toDouble(), input)
            val movementYaw = strafeEvent.post().strafeYaw

            // No changes are needed, when we don't modify the yaw used to move the player
            // val config = currentContext?.config ?: return@runSafe
            // if (config.rotationMode == RotationMode.SILENT && !input.handledByBaritone && baritoneContext == null) return@runSafe

            // Sign it to remove previous speed modifier
            val signForward = sign(input.movementForward)
            val signStrafe = sign(input.movementSideways)

            // No changes are needed when no inputs are pressed
            if (signForward == 0f && signStrafe == 0f) return@runSafe

            // Actual yaw used by the physics engine
            var actualYaw = currentRotation.yaw

            if (currentContext?.config?.rotationMode == RotationMode.SILENT) {
                actualYaw = player.yaw.toDouble()
            }

            val yawRad = (movementYaw - actualYaw).toRadian()

            val cosDelta = cos(yawRad)
            val sinDelta = sin(yawRad)

            val newX = signStrafe * cosDelta - signForward * sinDelta
            val newZ = signForward * cosDelta + signStrafe * sinDelta

            // Apply new movement
            input.apply {
                // Movement speed modifier
                val multiplier = if (slowDown) slowDownFactor else 1f

                movementSideways = round(newX).toFloat() * multiplier
                movementForward = round(newZ).toFloat() * multiplier
            }

            baritoneYaw ?: return@runSafe

            // Makes baritone movement safe
            // when yaw difference is too big to compensate it by modifying keyboard input
            val minYawDist = movementYawList
                .map { currentRotation.yaw + it } // all possible movement directions (including diagonals)
                .minOf { angleDifference(it, baritoneYaw) }

            if (minYawDist > 5.0) {
                input.movementSideways = 0f
                input.movementForward = 0f
            }
        }
    }
}
