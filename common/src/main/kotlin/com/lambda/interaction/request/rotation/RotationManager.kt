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

package com.lambda.interaction.request.rotation

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.rotation.Rotation.Companion.slerp
import com.lambda.interaction.request.rotation.visibilty.lookAt
import com.lambda.module.modules.client.Baritone
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.extension.partialTicks
import com.lambda.util.extension.rotation
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import net.minecraft.client.input.Input
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

object RotationManager : RequestHandler<RotationRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    // ToDo: Post interact
) {
    var activeRotation = Rotation.ZERO
    var serverRotation = Rotation.ZERO
    var prevServerRotation = Rotation.ZERO

    var activeRequest: RotationRequest? = null
    private var changedThisTick = false

    fun Any.onRotate(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Rotation>(priority, alwaysListen) {
        block()
    }

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            activeRequest?.let { request ->
                request.age++
            }
            changedThisTick = false
        }

        listen<PacketEvent.Receive.Post>(priority = Int.MIN_VALUE) { event ->
            val packet = event.packet
            if (packet !is PlayerPositionLookS2CPacket) return@listen

            runGameScheduled {
                reset(Rotation(packet.yaw, packet.pitch))
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            reset(Rotation.ZERO)
        }

        return "Loaded Rotation Manager"
    }

    override fun SafeContext.handleRequest(request: RotationRequest) {
        activeRequest?.let { if (it.age <= 0) return }
        if (request.target.targetRotation.value != null) {
            activeRequest = request
            updateActiveRotation()
            changedThisTick = true
        }
    }

    @JvmStatic
    fun processRotations() = runSafe {
        if (activeRequest != null) activeThisTick = true

        if (!changedThisTick) { // rebuild the rotation if the same context gets used again
            activeRequest?.target?.targetRotation?.update()
            updateActiveRotation()
        }

        // Tick and reset the context
        activeRequest?.let {
            if (--it.keepTicks > 0) return@let
            if (--it.decayTicks >= 0) return@let
            activeRequest = null
        }
    }

    fun onRotationSend() {
        prevServerRotation = serverRotation
        serverRotation = activeRotation/*.fixSensitivity(prevServerRotation)*/

        // Handle LOCK mode
        if (activeRequest?.mode == RotationMode.Lock) {
            mc.player?.yaw = serverRotation.yawF
            mc.player?.pitch = serverRotation.pitchF
        }
    }

    private fun SafeContext.updateActiveRotation() {
        activeRotation = activeRequest?.let { request ->
            val rotationTo = if (request.keepTicks >= 0)
                request.target.targetRotation.value
                    ?: activeRotation // same context gets used again && the rotation is null this tick
            else player.rotation

            val speedMultiplier = if (request.keepTicks < 0) 1.0 else request.speedMultiplier
            val turnSpeed = request.turnSpeed() * speedMultiplier

            serverRotation.slerp(rotationTo, turnSpeed)
        } ?: player.rotation
    }

    private fun reset(rotation: Rotation) {
        prevServerRotation = rotation
        serverRotation = rotation
        activeRotation = rotation
        activeRequest = null
    }

    private val smoothRotation
        get() =
            lerp(mc.partialTicks, prevServerRotation, serverRotation)

    @JvmStatic
    val lockRotation
        get() =
            if (activeRequest?.mode == RotationMode.Lock) smoothRotation else null

    @JvmStatic
    val renderYaw
        get() =
            if (activeRequest == null) null else smoothRotation.yaw.toFloat()

    @JvmStatic
    val renderPitch
        get() =
            if (activeRequest == null) null else smoothRotation.pitch.toFloat()

    @JvmStatic
    val handYaw
        get() =
            if (activeRequest?.mode == RotationMode.Lock) serverRotation.yaw.toFloat() else null

    @JvmStatic
    val handPitch
        get() =
            if (activeRequest?.mode == RotationMode.Lock) serverRotation.pitch.toFloat() else null

    @JvmStatic
    val movementYaw: Float?
        get() {
            if (activeRequest?.mode == RotationMode.Silent) return null
            return activeRotation.yaw.toFloat()
        }

    @JvmStatic
    val movementPitch: Float?
        get() {
            if (activeRequest?.mode == RotationMode.Silent) return null
            return activeRotation.pitch.toFloat()
        }

    @JvmStatic
    fun getRotationForVector(deltaTime: Double): Vec2d? {
        if (activeRequest?.mode == RotationMode.Silent) return null

        val rot = lerp(deltaTime, prevServerRotation, serverRotation)
        return Vec2d(rot.yaw, rot.pitch)
    }

    object BaritoneProcessor {
        private var baritoneContext: RotationRequest? = null

        private val movementYawList = arrayOf(
            0.0, 45.0,
            90.0, 135.0,
            180.0, 225.0,
            270.0, 315.0,
        )

        @JvmStatic
        fun handleBaritoneRotation(yaw: Float, pitch: Float) {
            baritoneContext = lookAt(Rotation(yaw, pitch)).requestBy(Baritone.rotation)
        }

        init {
            listenUnsafe<TickEvent.Pre> {
                baritoneContext = null
            }
        }

        @JvmStatic
        fun processPlayerMovement(input: Input, slowDown: Boolean, slowDownFactor: Float) = runSafe {
            // The yaw relative to which the movement was constructed
            val baritoneYaw = baritoneContext?.target?.targetRotation?.value?.yaw
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
            var actualYaw = activeRotation.yaw

            if (activeRequest?.mode == RotationMode.Silent) {
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
                .map { activeRotation.yaw + it } // all possible movement directions (including diagonals)
                .minOf { Rotation.angleDifference(it, baritoneYaw) }

            if (minYawDist > 5.0) {
                input.movementSideways = 0f
                input.movementForward = 0f
            }
        }
    }

    override fun preEvent(): Event = UpdateManagerEvent.Rotation().post()
}
