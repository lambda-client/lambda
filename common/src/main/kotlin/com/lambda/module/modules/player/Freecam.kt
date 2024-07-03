package com.lambda.module.modules.player

import baritone.utils.PlayerMovementInput
import com.lambda.Lambda.mc
import com.lambda.config.groups.RotationSettings
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.MovementUtils.cancel
import com.lambda.util.player.MovementUtils.verticalMovement
import com.lambda.util.primitives.extension.interpolate
import com.lambda.util.primitives.extension.partialTicks
import com.lambda.util.primitives.extension.rotation
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import com.lambda.util.world.raycast.RayCastUtils.orNull
import net.minecraft.client.input.KeyboardInput
import net.minecraft.client.option.Perspective
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

object Freecam : Module(
    name = "Freecam",
    description = "Move your camera freely",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val speed by setting("Speed", 0.5f, 0.1f..1.0f, 0.1f)
    private val sprint by setting("Sprint Multiplier", 3.0f, 0.1f..10.0f, 0.1f, description = "Set below 1.0 to fly slower on sprint.")
    private val reach by setting("Reach", 10.0, 1.0..100.0, 1.0, "Freecam reach distance")
    private val rotateToTarget by setting("Rotate to target", true)

    private val rotationConfig = RotationSettings(this) {
        rotateToTarget
    }.apply {
        rotationMode = RotationMode.LOCK
    }

    private var lastPerspective = Perspective.FIRST_PERSON
    private var prevPosition: Vec3d = Vec3d.ZERO
    private var position: Vec3d = Vec3d.ZERO
    private val interpolatedPosition: Vec3d
        get() = prevPosition.interpolate(position, mc.partialTicks)

    private var rotation: Rotation = Rotation.ZERO
    private var velocity: Vec3d = Vec3d.ZERO

    @JvmStatic
    fun updateCam() {
        mc.gameRenderer.apply {
            camera.setRotation(rotation.yawF, rotation.pitchF)
            camera.setPos(interpolatedPosition.x, interpolatedPosition.y, interpolatedPosition.z)
        }
    }

    /**
     * @see net.minecraft.entity.Entity.changeLookDirection
     */
    private const val SENSITIVITY_FACTOR = 0.15

    init {
        onEnable {
            lastPerspective = mc.options.perspective
            mc.options.perspective = Perspective.FIRST_PERSON
            position = player.eyePos
            rotation = player.rotation
            velocity = Vec3d.ZERO
        }

        onDisable {
            mc.options.perspective = lastPerspective
        }

        listener<RotationEvent.Update>(Int.MAX_VALUE) {
            if (!rotateToTarget) return@listener
            val target = mc.crosshairTarget?.orNull ?: return@listener

            val rotation = player.eyePos.rotationTo(target.pos)
            it.context = RotationContext(rotation, rotationConfig)
        }

        listener<EntityEvent.ChangeLookDirection> {
            rotation = rotation.withDelta(
                it.deltaYaw * SENSITIVITY_FACTOR,
                it.deltaPitch * SENSITIVITY_FACTOR
            )
            it.cancel()
        }

        listener<MovementEvent.InputUpdate> { event ->
            // Don't block baritone from working
            if (event.input !is PlayerMovementInput) {
                // Reset actual input
                event.input.cancel()
            }

            // Create new input for freecam
            val input = KeyboardInput(mc.options).apply {
                tick(false, 1f)
            }

            val inputVec = Vec3d(
                input.movementSideways.toDouble(),
                input.verticalMovement.toDouble(),
                input.movementForward.toDouble()
            )

            val endSpeed = speed * if (mc.options.sprintKey.isPressed) sprint else 1.0f
            val velocityDelta = Entity.movementInputToVelocity(inputVec, endSpeed, rotation.yawF)

            // Move freecam
            velocity = velocity.add(velocityDelta).multiply(0.6)
            prevPosition = position
            position = position.add(velocity)
        }

        listener<RenderEvent.UpdateTarget> {
            it.cancel()

            mc.crosshairTarget = rotation
                .rayCast(reach, eye = interpolatedPosition)
                .orMiss // Can't be null (otherwise mc will spam "Null returned as 'hitResult', this shouldn't happen!")
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
        }
    }
}
