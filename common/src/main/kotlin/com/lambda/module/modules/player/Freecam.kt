package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.config.RotationSettings
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.interpolate
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationMode
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.KeyCode
import com.lambda.util.player.MovementUtils.cancel
import com.lambda.util.primitives.extension.interpolate
import com.lambda.util.primitives.extension.rotation
import com.lambda.util.world.raycast.RayCastMask
import com.lambda.util.world.raycast.RayCastUtils.rayCast
import net.minecraft.client.input.KeyboardInput
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

object Freecam : Module(
    name = "Freecam",
    description = "Move your camera freely",
    defaultTags = setOf(ModuleTag.RENDER),
    defaultKeybind = KeyCode.G
) {
    private val speed by setting("Speed", 0.5, 0.1..1.0, 0.1)
    private val rotateToTarget by setting("Rotate to target", true)

    private val rotationConfig = RotationSettings(this).apply {
        rotationMode = RotationMode.LOCK
    }

    private var prevPosition: Vec3d = Vec3d.ZERO
    @JvmStatic var position: Vec3d = Vec3d.ZERO
    private val interpolatedPosition: Vec3d
        get() = prevPosition.interpolate(position, mc.tickDelta.toDouble())

    private var previousRotation: Rotation = Rotation.ZERO
    @JvmStatic var rotation: Rotation = Rotation.ZERO
    private val interpolatedRotation: Rotation
        get() = previousRotation.interpolate(rotation, mc.tickDelta.toDouble())

    private var velocity: Vec3d = Vec3d.ZERO

    @JvmStatic fun updateCam() {
        mc.gameRenderer.camera.apply {
            setRotation(interpolatedRotation.yaw.toFloat(), interpolatedRotation.pitch.toFloat())
            setPos(interpolatedPosition.x, interpolatedPosition.y, interpolatedPosition.z)
        }
    }

    @JvmStatic fun updateRotation(deltaYaw: Double, deltaPitch: Double) {
        previousRotation = rotation
        val factor = (mc.options.mouseSensitivity.value * 0.6 + 0.2).pow(3)
        rotation = rotation.withDelta(deltaYaw * factor, deltaPitch * factor)
    }

    @JvmStatic fun updateTarget() {
        runSafe {
            mc.crosshairTarget = rayCast(
                interpolatedPosition,
                interpolatedRotation.vector,
                interaction.reachDistance.toDouble(),
                RayCastMask.BOTH,
                true
            )
        }
    }

    init {
        onEnable {
            position = player.eyePos
            rotation = player.rotation
            velocity = Vec3d.ZERO
        }

        listener<RotationEvent.Pre> {
            if (!rotateToTarget) return@listener
            val target = mc.crosshairTarget ?: return@listener

            val rotation = player.eyePos.rotationTo(target.pos)
            it.requests.add(RotationRequest(rotationConfig, rotation))
        }

        listener<MovementEvent.InputUpdate> { event ->
            event.cancel()

            // Reset actual input
            player.input.cancel()

            // Create new input for freecam
            val input = KeyboardInput(mc.options)
            input.tick(event.slowDown, event.slowDownFactor)
            var y = 0.0
            if (input.jumping) y++
            if (input.sneaking) y--
            val inputVec = Vec3d(input.movementSideways.toDouble(), y, input.movementForward.toDouble())
            val velocityDelta = Entity.movementInputToVelocity(inputVec, speed.toFloat(), rotation.yaw.toFloat())

            // move freecam
            velocity = velocity.add(velocityDelta).multiply(0.6)
            prevPosition = position
            position = position.add(velocity)
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
        }
    }
}