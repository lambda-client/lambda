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

package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.config.groups.RotationConfig
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.interpolate
import com.lambda.util.extension.partialTicks
import com.lambda.util.extension.rotation
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.player.MovementUtils.calcMoveRad
import com.lambda.util.player.MovementUtils.cancel
import com.lambda.util.player.MovementUtils.handledByBaritone
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.movementVector
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.verticalMovement
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import com.lambda.util.world.raycast.RayCastUtils.orNull
import net.minecraft.client.option.Perspective
import net.minecraft.util.math.Vec3d

object Freecam : Module(
    name = "Freecam",
    description = "Move your camera freely",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val speed by setting("Speed", 0.5, 0.1..1.0, 0.1)
    private val sprint by setting("Sprint Multiplier", 3.0, 0.1..10.0, 0.1, description = "Set below 1.0 to fly slower on sprint.")
    private val reach by setting("Reach", 10.0, 1.0..100.0, 1.0, "Freecam reach distance")
    private val rotateToTarget by setting("Rotate to target", true)

    private val rotationConfig = object : RotationConfig.Instant {
        override val rotationMode = RotationMode.LOCK
    }

    private var lastPerspective = Perspective.FIRST_PERSON
    private var prevPosition: Vec3d = Vec3d.ZERO
    private var position: Vec3d = Vec3d.ZERO
    private val lerpPos: Vec3d
        get() = prevPosition.interpolate(position, mc.partialTicks)

    private var rotation: Rotation = Rotation.ZERO
    private var velocity: Vec3d = Vec3d.ZERO

    @JvmStatic
    fun updateCam() {
        mc.gameRenderer.apply {
            camera.setRotation(rotation.yawF, rotation.pitchF)
            camera.setPos(lerpPos.x, lerpPos.y, lerpPos.z)
        }
    }

    /**
     * @see net.minecraft.entity.Entity.changeLookDirection
     */
    private const val SENSITIVITY_FACTOR = 0.15

    init {
        onEnable {
            lastPerspective = mc.options.perspective
            position = player.eyePos
            rotation = player.rotation
            velocity = Vec3d.ZERO
        }

        onDisable {
            mc.options.perspective = lastPerspective
        }

        listen<RotationEvent.Update>(Int.MAX_VALUE) { event ->
            if (!rotateToTarget) return@listen
            val target = mc.crosshairTarget?.orNull ?: return@listen

            val rotation = player.eyePos.rotationTo(target.pos)
            event.request = RotationRequest(rotation, rotationConfig)
        }

        listen<PlayerEvent.ChangeLookDirection> {
            rotation = rotation.withDelta(
                it.deltaYaw * SENSITIVITY_FACTOR,
                it.deltaPitch * SENSITIVITY_FACTOR
            )
            it.cancel()
        }

        listen<MovementEvent.InputUpdate> { event ->
            mc.options.perspective = Perspective.FIRST_PERSON

            // Don't block baritone from working
            if (!event.input.handledByBaritone) {
                // Reset actual input
                event.input.cancel()
            }

            // Create new input for freecam
            val input = newMovementInput(assumeBaritoneUsage = false, slowDownCheck = false)
            val sprintModifier = if (mc.options.sprintKey.isPressed) sprint else 1.0
            val moveDir = calcMoveRad(rotation.yawF, input.roundedForward, input.roundedStrafing)
            var moveVec = movementVector(moveDir, input.verticalMovement) * speed * sprintModifier
            if (!input.isInputting) moveVec *= Vec3d(0.0, 1.0, 0.0)

            // Apply movement
            velocity += moveVec
            velocity *= 0.6

            // Update position
            prevPosition = position
            position += velocity
        }

        listen<RenderEvent.UpdateTarget> {
            it.cancel()

            mc.crosshairTarget = rotation
                .rayCast(reach, lerpPos)
                .orMiss // Can't be null (otherwise mc will spam "Null returned as 'hitResult', this shouldn't happen!")
        }

        listen<ConnectionEvent.Disconnect> {
            disable()
        }
    }
}
