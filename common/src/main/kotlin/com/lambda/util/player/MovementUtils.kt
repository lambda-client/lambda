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

package com.lambda.util.player

import com.lambda.context.SafeContext
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.util.math.MathUtils.toDegree
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.plus
import com.lambda.util.math.times
import net.minecraft.client.input.Input
import net.minecraft.client.input.KeyboardInput
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.util.PlayerInput
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

object MovementUtils {
    /**
     * The forward value is independent of the player's rotation; normalized between -1 and 1
     */
    var Input.forward get() = movementVector.y; set(value) { movementVector = Vec2f(movementVector.x, value) }

    /**
     * The forward value is independent of the player's rotation; normalized between -1 and 1
     */
    var Input.strafe get() = movementVector.x; set(value) { movementVector = Vec2f(value, movementVector.y) }
    var Input.jumping get() = playerInput.jump; set(value) { update(jump = value) }
    var Input.sneaking get() = playerInput.sneak; set(value) { update(sneak = value) }
    var Input.sprinting get() = playerInput.sprint; set(value) { update(sprint = value) }

    /**
     * Returns the absolute direction of the forward scalar
     */
    val Input.roundedForward get() = sign(movementVector.y).toDouble()

    /**
     * Returns the absolute direction of the strafe scalar
     */
    val Input.roundedStrafing get() = sign(movementVector.x).toDouble()

    val Input.handledByBaritone get() = this !is KeyboardInput

    val Input.isInputting get() = forward != 0f || strafe != 0f
    val SafeContext.isInputting get() = player.input.isInputting

    fun SafeContext.newMovementInput(
        assumeBaritone: Boolean = true,
        slowdownCheck: Boolean = true,
    ): Input {
        if (assumeBaritone && player.input.handledByBaritone) return player.input

        val newInput = KeyboardInput(mc.options)

        if (!slowdownCheck) return newInput.apply { tick() }

        newInput.movementVector = player.applyMovementSpeedFactors(player.input.movementVector)

        return newInput
    }

    @Deprecated(message = "mergeFrom is deprecated in favor of Input.update", replaceWith = ReplaceWith("this.update()"))
    fun Input.mergeFrom(input: Input) {
        playerInput = input.playerInput
        movementVector = input.movementVector
    }

    fun Input.update(
        forward: Double = movementVector.y.toDouble(),
        strafe: Double = movementVector.x.toDouble(),
        jump: Boolean = playerInput.jump,
        sneak: Boolean = playerInput.sneak,
        sprint: Boolean = playerInput.sprint,
    ) {
        val input = buildMovementInput(forward, strafe, jump, sneak, sprint)

        movementVector = input.movementVector
        playerInput = input.playerInput
    }

    fun buildMovementInput(
        forward: Double,
        strafe: Double,
        jump: Boolean = false,
        sneak: Boolean = false,
        sprint: Boolean = false,
    ) = Input().apply {
        movementVector = Vec2f(strafe.toFloat(), forward.toFloat())
        playerInput = PlayerInput(
            forward > 0.0,
            forward < 0.0,
            strafe < 0.0,
            strafe > 0.0,
            jump,
            sneak,
            sprint,
        )
    }

    fun Input.cancel(vertical: Boolean = true) =
        update(0.0, 0.0, !vertical, !vertical, false)

    val Input.verticalMovement
        get() = (playerInput.jump.toInt() - playerInput.sneak.toInt()).toDouble()

    private fun inputMoveOffset(
        moveForward: Double,
        moveStrafe: Double,
    ) = atan2(-moveStrafe, moveForward)

    fun SafeContext.calcMoveYaw(
        yawIn: Float = player.moveYaw,
        moveForward: Double = player.input.roundedForward,
        moveStrafe: Double = player.input.roundedStrafing,
    ) = yawIn + inputMoveOffset(moveForward, moveStrafe).toDegree()

    fun SafeContext.calcMoveRad(
        yawIn: Float = player.moveYaw,
        moveForward: Double = player.input.roundedForward,
        moveStrafe: Double = player.input.roundedStrafing,
    ) = yawIn.toRadian() + inputMoveOffset(moveForward, moveStrafe)

    fun SafeContext.movementVector(radDir: Double = calcMoveRad(), y: Double = 0.0) =
        Vec3d(-sin(radDir), y, cos(radDir))

    var Entity.motion: Vec3d
        get() = velocity
        set(value) {
            velocity = value
        }
    var Entity.motionX get() = velocity.x; set(value) = setVelocity(value, velocity.y, velocity.z)
    var Entity.motionY get() = velocity.y; set(value) = setVelocity(velocity.x, value, velocity.z)
    var Entity.motionZ get() = velocity.z; set(value) = setVelocity(velocity.x, velocity.y, value)

    fun SafeContext.setSpeed(speed: Double, direction: Double = calcMoveRad()) {
        player.motion = movementVector(direction, player.motionY)
        mulSpeed(speed)
    }

    fun SafeContext.addSpeed(speed: Double, direction: Double = calcMoveRad()) {
        player.motion += movementVector(direction) * speed
    }

    fun SafeContext.mulSpeed(modifier: Double) {
        player.motion *= Vec3d(modifier, 1.0, modifier)
    }

    val ClientPlayerEntity.moveYaw get() = RotationManager.movementYaw ?: yaw

    val Entity.moveDiff get() = Vec3d(x - lastX, y - lastY, z - lastZ)
    val Entity.moveDelta get() = moveDiff.let { hypot(it.x, it.z) }

    val Entity.octant: EightWayDirection
        get() = yaw.octant

    val Float.octant: EightWayDirection
        get() {
            // Normalize the yaw to be within the range of -180 to 179 degrees
            var normalizedYaw = (this + 180.0) % 360.0
            if (normalizedYaw < 0) {
                normalizedYaw += 360.0
            }

            // Calculate the index of the closest direction
            val directionIndex = ((normalizedYaw + 22.5) / 45.0).toInt() % 8
            return EightWayDirection.entries[directionIndex]
        }
}
