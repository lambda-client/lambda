package com.lambda.interaction

import baritone.utils.PlayerMovementInput
import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.config.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.angleDifference
import com.lambda.interaction.rotation.Rotation.Companion.slerp
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.modules.client.Baritone
import com.lambda.threading.runOnGameThread
import com.lambda.threading.runSafe
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.primitives.extension.partialTicks
import com.lambda.util.primitives.extension.rotation
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.MathHelper
import kotlin.math.roundToInt
import kotlin.math.sign

object RotationManager : Loadable {
    var currentRotation = Rotation.ZERO
    var prevRotation = Rotation.ZERO

    var currentContext: RotationContext? = null

    private var keepTicks = 0
    private var pauseTicks = 0

    @JvmStatic
    fun update() =
        runSafe {
            RotationEvent.Pre().post {
                rotate(context)
                currentContext?.let { RotationEvent.Post(it).post() }
            }
        }

    init {
        listener<PacketEvent.Send.Post> { event ->
            val packet = event.packet
            if (packet !is PlayerPositionLookS2CPacket) return@listener

            runOnGameThread {
                reset(Rotation(packet.yaw, packet.pitch))
            }
        }

        unsafeListener<ConnectionEvent.Disconnect> {
            reset(Rotation.ZERO)
        }
    }

    private fun rotate(newContext: RotationContext?) = runSafe {
        prevRotation = currentRotation

        (keepTicks--).coerceAtLeast(0)
        (pauseTicks--).coerceAtLeast(0)

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

            prevRotation
                .slerp(rotationTo, turnSpeed)
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

    private val smoothRotation get() =
        lerp(prevRotation, currentRotation, mc.partialTicks)

    @JvmStatic val lockRotation get() =
        if (currentContext?.config?.rotationMode == RotationMode.LOCK) smoothRotation else null

    @JvmStatic val renderYaw get() =
        if (currentContext?.config == null) null else smoothRotation.yaw.toFloat()

    @JvmStatic val renderPitch get() =
        if (currentContext?.config == null) null else smoothRotation.pitch.toFloat()

    @JvmStatic val handYaw get() =
        if (currentContext?.config?.rotationMode == RotationMode.LOCK) currentRotation.yaw.toFloat() else null

    @JvmStatic val handPitch get() =
        if (currentContext?.config?.rotationMode == RotationMode.LOCK) currentRotation.pitch.toFloat() else null

    @JvmStatic val movementYaw: Float? get() {
        val config = currentContext?.config ?: return null
        if (config.rotationMode == RotationMode.SILENT) return null
        return currentRotation.yaw.toFloat()
    }

    @JvmStatic val movementPitch: Float? get() {
        val config = currentContext?.config ?: return null
        if (config.rotationMode == RotationMode.SILENT) return null
        return currentRotation.pitch.toFloat()
    }

    @JvmStatic fun getRotationForVector(deltaTime: Double): Vec2d? {
        val config = currentContext?.config ?: return null
        if (config.rotationMode == RotationMode.SILENT) return null

        val rot = lerp(prevRotation, currentRotation, deltaTime)
        return Vec2d(rot.yaw, rot.pitch)
    }

    object BaritoneProcessor {
        var baritoneContext: RotationContext? = null; private set

        private val movementYawList = arrayOf(
            0.0, 45.0,
            90.0, 135.0,
            180.0, 225.0,
            270.0, 315.0,
        )

        init {
            listener<TickEvent.Post> {
                baritoneContext = null
            }

            listener<MovementEvent.InputUpdate> {
                processPlayerMovement(it)
            }
        }

        @JvmStatic
        fun handleBaritoneRotation(yaw: Float, pitch: Float) = runSafe {
            baritoneContext = RotationContext(Rotation(yaw, pitch), Baritone.rotation)
        }

        private fun SafeContext.processPlayerMovement(event: MovementEvent.InputUpdate) {
            val config = currentContext?.config ?: return

            // No changes are needed, when we don't modify the yaw used to move the player
            if (config.rotationMode == RotationMode.SILENT) return

            val input = event.input
            val handledByBaritone = input is PlayerMovementInput

            // Sign it to remove previous speed modifier
            val signForward = sign(input.movementForward)
            val signStrafe = sign(input.movementSideways)

            // No changes are needed when no inputs are pressed
            if (signForward == 0f && signStrafe == 0f) return

            // Movement speed modifier
            val multiplier = if (event.slowDown) event.slowDownFactor else 1f

            val modifyMovement = config.rotationMode == RotationMode.SYNC || handledByBaritone
            if (!modifyMovement) return

            val playerYaw = player.yaw.toDouble()
            val baritoneYaw = if (handledByBaritone) baritoneContext?.rotation?.yaw else null

            // The yaw relative to which the movement was constructed
            val movementYaw = baritoneYaw ?: playerYaw

            // Actual yaw used to move the player
            val actualYaw = currentRotation.yaw

            val yawRad = (movementYaw - actualYaw).toRadian().toFloat()

            val cosDelta = MathHelper.cos(yawRad)
            val sinDelta = MathHelper.sin(yawRad)

            val newX = signStrafe * cosDelta - signForward * sinDelta
            val newZ = signForward * cosDelta + signStrafe * sinDelta

            // Apply new movement
            input.apply {
                movementSideways = newX.roundToInt().toFloat() * multiplier
                movementForward = newZ.roundToInt().toFloat() * multiplier
            }

            baritoneYaw ?: return

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