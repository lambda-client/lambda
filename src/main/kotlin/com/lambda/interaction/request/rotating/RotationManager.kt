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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.request.Logger
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.rotating.Rotation.Companion.slerp
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.hud.ManagerDebugLoggers.rotationManagerLogger
import com.lambda.module.modules.client.Baritone
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.extension.partialTicks
import com.lambda.util.extension.rotation
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import net.minecraft.client.input.Input
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.PlayerInput
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Vec2f
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object RotationManager : RequestHandler<RotationRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
), Logger {
    var activeRotation = Rotation.ZERO
    var serverRotation = Rotation.ZERO
    @JvmStatic
    var prevServerRotation = Rotation.ZERO

    var activeRequest: RotationRequest? = null
    private var changedThisTick = false

    override val logger = rotationManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            activeRequest?.let {
                if (it.keepTicks <= 0 && it.decayTicks <= 0) {
                    logger.debug("Clearing active request ${it.requestID}")
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

        listen<RenderEvent.UpdateTarget> {
            if (activeRequest == null) return@listen
            it.cancel()

            val eye = player.eyePos
            val entityHit = player.rotation.rayCast(player.entityInteractionRange, eye).orMiss
            mc.targetedEntity = (entityHit as? EntityHitResult)?.entity
            val blockHit = player.rotation.rayCast(player.blockInteractionRange, eye).orMiss
            mc.crosshairTarget = blockHit
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

        // Override user interactions with max priority
        listen<PlayerEvent.Interact.Block>(priority = Int.MAX_VALUE) {
            activeRotation = player.rotation
        }

        listen<PlayerEvent.Attack.Block>(priority = Int.MAX_VALUE) {
            activeRotation = player.rotation
        }

        listen<PlayerEvent.Interact.Entity>(priority = Int.MAX_VALUE) {
            activeRotation = player.rotation
        }

        listen<PlayerEvent.Attack.Entity>(priority = Int.MAX_VALUE) {
            activeRotation = player.rotation
        }

        listen<PlayerEvent.Interact.Item>(priority = Int.MAX_VALUE) {
            activeRotation = player.rotation
        }

        return "Loaded Rotation Manager"
    }

    override fun SafeContext.handleRequest(request: RotationRequest) {
        activeRequest?.let { if (it.age <= 0) return }
        if (request.target.targetRotation.value != null) {
            logger.debug("Accepting request ${request.requestID}")
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

    @JvmStatic
    fun handleBaritoneRotation(yaw: Float, pitch: Float) {
        lookAt(Rotation(yaw, pitch)).requestBy(BaritoneManager.rotation)
    }

    @JvmStatic
    fun redirectStrafeInputs(input: Input) = runSafe {
        val movementYaw = movementYaw ?: return@runSafe
        val playerYaw = player.yaw

        if (movementYaw.minus(playerYaw).rem(360f).let { it * it } < 0.001f) return@runSafe

        val originalStrafe = input.movementVector.x
        val originalForward = input.movementVector.y

        if (originalStrafe == 0.0f && originalForward == 0.0f) return@runSafe

        val deltaYawRad = (playerYaw - movementYaw).toRadian()

        val cos = cos(deltaYawRad)
        val sin = sin(deltaYawRad)
        val newStrafe = originalStrafe * cos - originalForward * sin
        val newForward = originalStrafe * sin + originalForward * cos

        val angle = atan2(newStrafe.toDouble(), newForward.toDouble())

        // Define the boundaries for our 8 sectors (in radians). Each sector is 45 degrees (PI/4).
        val sector = (PI / 4.0).toFloat()
        val boundary = (PI / 8.0).toFloat() // The halfway point between sectors (22.5 degrees)

        var pressForward = false
        var pressBackward = false
        var pressLeft = false
        var pressRight = false

        // Determine which 45-degree sector the angle falls into and set the corresponding keys.
        when {
            angle > -boundary && angle <= boundary -> {
                pressForward = true
            }
            angle > boundary && angle <= boundary + sector -> {
                pressForward = true
                pressLeft = true
            }
            angle > boundary + sector && angle <= boundary + 2 * sector -> {
                pressLeft = true
            }
            angle > boundary + 2 * sector && angle <= boundary + 3 * sector -> {
                pressBackward = true
                pressLeft = true
            }
            angle > boundary + 3 * sector || angle <= -(boundary + 3 * sector) -> {
                pressBackward = true
            }
            angle > -(boundary + 3 * sector) && angle <= -(boundary + 2 * sector) -> {
                pressBackward = true
                pressRight = true
            }
            angle > -(boundary + 2 * sector) && angle <= -(boundary + sector) -> {
                pressRight = true
            }
            angle > -(boundary + sector) && angle <= -boundary -> {
                pressForward = true
                pressRight = true
            }
        }

        input.playerInput = PlayerInput(
            pressForward,
            pressBackward,
            pressLeft,
            pressRight,
            input.playerInput.jump(),
            input.playerInput.sneak(),
            input.playerInput.sprint()
        )

        val x = multiplier(input.playerInput.left(), input.playerInput.right())
        val y = multiplier(input.playerInput.forward(), input.playerInput.backward())
        input.movementVector = Vec2f(x, y).normalize()
    }

    private fun multiplier(positive: Boolean, negative: Boolean) =
        ((if (positive) 1 else 0) - (if (negative) 1 else 0)).toFloat()

    fun onRotationSend() {
        prevServerRotation = serverRotation
        serverRotation = activeRotation

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

            // Important: do NOT wrap the result yaw; keep it continuous to match vanilla packets
            serverRotation.slerp(rotationTo, turnSpeed)
        } ?: player.rotation

        logger.debug("Active rotation set to $activeRotation")
    }

    private fun reset(rotation: Rotation) {
        logger.debug("Resetting values")
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

    override fun preEvent() = UpdateManagerEvent.Rotation.post()
}
