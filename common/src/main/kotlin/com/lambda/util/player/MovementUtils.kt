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

package com.lambda.util.player

import com.lambda.context.SafeContext
import com.lambda.interaction.RotationManager
import com.lambda.util.math.MathUtils.toDegree
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.plus
import com.lambda.util.math.times
import net.minecraft.client.input.Input
import net.minecraft.client.input.KeyboardInput
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.EnchantmentHelper.getSwiftSneakSpeedBoost
import net.minecraft.entity.Entity
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec3d
import kotlin.math.*

object MovementUtils {
    val Input.roundedForward get() = sign(movementForward).toDouble()
    val Input.roundedStrafing get() = sign(movementSideways).toDouble()
    val Input.handledByBaritone get() = this !is KeyboardInput

    val Input.isInputting get() = roundedForward != 0.0 || roundedStrafing != 0.0
    val SafeContext.isInputting get() = player.input.isInputting

    fun SafeContext.newMovementInput(
        assumeBaritoneUsage: Boolean = true,
        slowDownCheck: Boolean = true,
    ): Input = if (assumeBaritoneUsage && player.input.handledByBaritone) {
        player.input
    } else {
        var multiplier = 1f

        if (slowDownCheck && player.shouldSlowDown()) multiplier =
            0.3f + getSwiftSneakSpeedBoost(player)

        KeyboardInput(mc.options).apply {
            tick(true, multiplier.coerceIn(0f, 1f))
        }
    }

    fun buildMovementInput(
        forward: Double,
        strafe: Double,
        jump: Boolean = false,
        sneak: Boolean = false,
    ) = Input().apply {
        movementForward = forward.toFloat()
        movementSideways = strafe.toFloat()

        pressingForward = forward > 0.0
        pressingBack = forward < 0.0
        pressingLeft = strafe < 0.0
        pressingRight = strafe > 0.0

        jumping = jump
        sneaking = sneak
    }

    fun Input.mergeFrom(input: Input) {
        movementForward = input.movementForward
        movementSideways = input.movementSideways

        pressingForward = input.pressingForward
        pressingBack = input.pressingBack
        pressingLeft = input.pressingLeft
        pressingRight = input.pressingRight

        jumping = input.jumping
        sneaking = input.sneaking
    }

    fun Input.cancel(cancelVertical: Boolean = true) {
        movementForward = 0f
        movementSideways = 0f

        pressingForward = false
        pressingBack = false
        pressingLeft = false
        pressingRight = false

        if (cancelVertical) {
            jumping = false
            sneaking = false
        }
    }

    val Input.verticalMovement
        get() =
            (jumping.toInt() - sneaking.toInt()).toDouble()

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

    var Entity.motion
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

    val Entity.moveDiff get() = Vec3d(this.pos.x - this.prevX, this.pos.y - this.prevY, this.pos.z - this.prevZ)
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
