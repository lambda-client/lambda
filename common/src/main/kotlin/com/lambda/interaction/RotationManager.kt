package com.lambda.interaction

import com.lambda.Lambda.mc
import com.lambda.Loadable
import com.lambda.config.RotationSettings
import com.lambda.event.EventFlow
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.interaction.rotation.*
import com.lambda.interaction.rotation.Rotation.Companion.angleDifference
import com.lambda.interaction.rotation.Rotation.Companion.fixSensitivity
import com.lambda.interaction.rotation.Rotation.Companion.interpolate
import com.lambda.module.modules.client.Baritone
import com.lambda.threading.runOnGameThread
import com.lambda.threading.runSafe
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.primitives.extension.partialTicks
import net.minecraft.client.input.KeyboardInput
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.MathHelper
import kotlin.math.*

object RotationManager : Loadable {
    var currentRotation = Rotation.ZERO
    private var prevRotation = Rotation.ZERO

    var currentRequest: RotationRequest? = null

    private var keepTicks = 0
    private var pauseTicks = 0

    @JvmStatic
    fun update() =
        runSafe {
            EventFlow.post(RotationEvent.Pre()) {
                rotate()?.let {
                    EventFlow.post(RotationEvent.Post(it))
                }
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

        listener<RenderEvent.UpdateTarget> {
            if (currentRequest == null) return@listener
            if (currentRequest?.config?.rotationMode != RotationMode.LOCK) return@listener
            val interpolation = prevRotation.interpolate(currentRotation, mc.tickDelta.toDouble())

//        val rot = interpolation.fixSensitivity(prevRotation)

            player.yaw = interpolation.yaw.toFloat()
            player.pitch = interpolation.pitch.toFloat()
        }

        unsafeListener<ConnectionEvent.Disconnect> {
            reset(Rotation.ZERO)
        }
    }

    private fun RotationEvent.Pre.rotate() = runSafe {
        prevRotation = currentRotation

        (keepTicks--).coerceAtLeast(0)
        (pauseTicks--).coerceAtLeast(0)

        val resetTicks = currentRequest?.config?.resetTicks ?: 0
        if (keepTicks + resetTicks < 0 || pauseTicks >= 0) {
            currentRequest = null
        }

        var chosenRequest: RotationRequest? = null

        requests.firstOrNull()?.let { request ->
            chosenRequest = request
            currentRequest = request
            keepTicks = request.config.keepTicks
        }

        currentRotation = Rotation(player.yaw, player.pitch)

        val context = currentRequest ?: return@runSafe chosenRequest
        val rotationTo = if (keepTicks >= 0) context.rotation else currentRotation

        var speedMultiplier = (context.config as? RotationSettings)?.speedMultiplier ?: 1.0
        if (keepTicks < 0) speedMultiplier = 1.0

        val turnSpeed = context.config.turnSpeed * speedMultiplier

        val interpolation = prevRotation.interpolate(rotationTo, turnSpeed)

        currentRotation = interpolation.fixSensitivity(prevRotation)

        if (context.config.rotationMode == RotationMode.LOCK) {
            player.yaw = currentRotation.yaw.toFloat()
            player.pitch = currentRotation.pitch.toFloat()
        }

        chosenRequest?.let { request ->
            if (request.rotation.fixSensitivity(prevRotation) == currentRotation) {
                request.isPending = false
            }
        }

        return@runSafe chosenRequest
    }

    private fun reset(rotation: Rotation) {
        prevRotation = rotation
        currentRotation = rotation

        currentRequest = null
        pauseTicks = 3
    }

    private val smoothRotation get() =
        lerp(prevRotation, currentRotation, mc.partialTicks)

    @JvmStatic val lockRotation get() =
        if (currentRequest?.config?.rotationMode == RotationMode.LOCK) smoothRotation else null

    @JvmStatic val renderYaw get() =
        if (currentRequest?.config == null) null else smoothRotation.yaw.toFloat()

    @JvmStatic val renderPitch get() =
        if (currentRequest?.config == null) null else smoothRotation.pitch.toFloat()

    @JvmStatic val handYaw get() =
        if (currentRequest?.config?.rotationMode == RotationMode.LOCK) currentRotation.yaw.toFloat() else null

    @JvmStatic val handPitch get() =
        if (currentRequest?.config?.rotationMode == RotationMode.LOCK) currentRotation.pitch.toFloat() else null

    @JvmStatic val movementYaw: Float? get() {
        val config = currentRequest?.config ?: return null
        if (config.rotationMode == RotationMode.SILENT) return null
        return currentRotation.yaw.toFloat()
    }

    @JvmStatic val movementPitch: Float? get() {
        val config = currentRequest?.config ?: return null
        if (config.rotationMode == RotationMode.SILENT) return null
        return currentRotation.pitch.toFloat()
    }

    @JvmStatic fun getRotationForVector(deltaTime: Double): Vec2d? {
        val config = currentRequest?.config ?: return null
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
                processPlayerMovement()
            }
        }

        @JvmStatic
        fun handleBaritoneRotation(yaw: Float, pitch: Float) = runSafe {
            baritoneContext = RotationContext(Baritone.rotation, Rotation(yaw, pitch))
        }

        @JvmStatic
        fun processPlayerMovement() = runSafe {
            val config = currentRequest?.config ?: return@runSafe

            val input = player.input
            val handledByBaritone = input !is KeyboardInput

            // Sign it to remove previous speed modifier
            val signForward = sign(input.movementForward)
            val signStrafe = sign(input.movementSideways)

            // No changes are needed when no inputs are pressed
            if (signForward == 0f && signStrafe == 0f) return@runSafe

            // Movement speed modifier
            val multiplier = if (!player.shouldSlowDown()) 1f else
                (0.3f + EnchantmentHelper.getSwiftSneakSpeedBoost(player)).coerceIn(0f, 1f)

            // No changes are needed, when we don't modify the yaw used to move the player
            if (config.rotationMode == RotationMode.SILENT) return@runSafe

            val modifyMovement = config.rotationMode == RotationMode.SYNC || handledByBaritone
            if (!modifyMovement) return@runSafe

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
            input.movementSideways = newX.roundToInt().toFloat() * multiplier
            input.movementForward = newZ.roundToInt().toFloat() * multiplier

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