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

package com.lambda.interaction.request.rotating

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.rotating.Rotation.Companion.slerp
import com.lambda.interaction.request.rotating.Rotation.Companion.wrap
import com.lambda.interaction.request.rotating.visibilty.lookAt
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
import net.minecraft.util.math.Vec2f
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
) {
    var activeRotation = Rotation.ZERO
    var serverRotation = Rotation.ZERO
    var prevServerRotation = Rotation.ZERO

    var activeRequest: RotationRequest? = null
    private var changedThisTick = false

    fun Any.onRotate(
        alwaysListen: Boolean = false,
        priority: Int = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Rotation>(priority, alwaysListen) {
        block()
    }

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            activeRequest?.let {
                if (it.keepTicks <= 0 && it.decayTicks <= 0) {
                    activeRequest = null
                }
            }
        }

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
                reset(Rotation(packet.change.yaw, packet.change.pitch))
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
            if (it.keepTicks-- > 0) return@let
            it.decayTicks--
        }
    }

    fun onRotationSend() {
        prevServerRotation = serverRotation
        serverRotation = activeRotation/*.fixSensitivity(prevServerRotation)*/

        // Handle LOCK mode
        if (activeRequest?.rotationMode == RotationMode.Lock) {
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
            val turnSpeed = request.turnSpeed * speedMultiplier

            serverRotation.slerp(rotationTo, turnSpeed).wrap()
        } ?: player.rotation
    }

    private fun reset(rotation: Rotation) {
        prevServerRotation = rotation
        serverRotation = rotation
        activeRotation = rotation
        activeRequest = null
    }

    private val smoothRotation
        get() = lerp(mc.partialTicks, serverRotation, activeRotation)

    @JvmStatic
    val lockRotation
        get() = if (activeRequest?.rotationMode == RotationMode.Lock) smoothRotation else null

    @JvmStatic
    val headYaw
        get() = if (activeRequest == null) null else activeRotation.yawF

    @JvmStatic
    val headPitch
        get() = if (activeRequest == null) null else activeRotation.pitchF

    @JvmStatic
    val handYaw
        get() = if (activeRequest?.rotationMode == RotationMode.Lock) activeRotation.yawF else null

    @JvmStatic
    val handPitch
        get() = if (activeRequest?.rotationMode == RotationMode.Lock) activeRotation.pitchF else null

    @JvmStatic
    val movementYaw: Float?
        get() {
            if (activeRequest == null || activeRequest?.rotationMode == RotationMode.Silent) return null
            return activeRotation.yaw.toFloat()
        }

    @JvmStatic
    val movementPitch: Float?
        get() {
            if (activeRequest == null || activeRequest?.rotationMode == RotationMode.Silent) return null
            return activeRotation.pitch.toFloat()
        }

    @JvmStatic
    fun getRotationForVector(deltaTime: Double): Vec2d? {
        if (activeRequest == null || activeRequest?.rotationMode == RotationMode.Silent) return null

        val rot = lerp(deltaTime, serverRotation, activeRotation)
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
        fun processPlayerMovement(input: Input) = runSafe {
            // The yaw relative to which the movement was constructed
            val baritoneYaw = baritoneContext?.target?.targetRotation?.value?.yaw
            val strafeEvent = RotationEvent.StrafeInput(baritoneYaw ?: player.yaw.toDouble(), input)
            val movementYaw = strafeEvent.post().strafeYaw

            // No changes are needed, when we don't modify the yaw used to move the player
            // val config = currentContext?.config ?: return@runSafe
            // if (config.rotationMode == RotationMode.SILENT && !input.handledByBaritone && baritoneContext == null) return@runSafe

            // Sign it to remove previous speed modifier
            input.hasForwardMovement()
            val signForward = sign(input.movementVector.y)
            val signStrafe = sign(input.movementVector.x)

            // No changes are needed when no inputs are pressed
            if (signForward <= 1.0E-5f && signStrafe <= 1.0E-5F) return@runSafe

            // Actual yaw used by the physics engine
            var actualYaw = activeRotation.yaw

            if (activeRequest?.rotationMode == RotationMode.Silent) {
                actualYaw = player.yaw.toDouble()
            }

            val yawRad = (movementYaw - actualYaw).toRadian()

            val cosDelta = cos(yawRad)
            val sinDelta = sin(yawRad)

            val newX = signStrafe * cosDelta - signForward * sinDelta
            val newZ = signForward * cosDelta + signStrafe * sinDelta

            // Apply new movement
            input.apply {
                movementVector = Vec2f(
                    round(newX).toFloat(),
                    round(newZ).toFloat(),
                )
            }

            baritoneYaw ?: return@runSafe

            // Makes baritone movement safe
            // when yaw difference is too big to compensate it by modifying keyboard input
            val minYawDist = movementYawList
                .map { activeRotation.yaw + it } // all possible movement directions (including diagonals)
                .minOf { Rotation.angleDifference(it, baritoneYaw) }

            if (minYawDist > 5.0) input.movementVector = Vec2f.ZERO
        }
    }

    override fun preEvent() = UpdateManagerEvent.Rotation.post()
}
