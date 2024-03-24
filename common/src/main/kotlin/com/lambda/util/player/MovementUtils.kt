package com.lambda.util.player

import com.lambda.context.SafeContext
import com.lambda.interaction.RotationManager
import com.lambda.util.math.MathUtils.random
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import net.minecraft.client.input.Input
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

object MovementUtils {
    private val SafeContext.roundedForward get() = sign(player.input.movementForward)
    private val SafeContext.roundedStrafing get() = sign(player.input.movementSideways)

    fun Input.cancel() {
        movementForward = 0f
        movementSideways = 0f

        pressingForward = false
        pressingBack = false
        pressingLeft = false
        pressingRight = false
    }

    val SafeContext.isInputting: Boolean get() =
        roundedForward != 0f || roundedStrafing != 0f

    val SafeContext.verticalMovement get() =
        player.input.jumping.toInt() - player.input.sneaking.toInt()

    fun SafeContext.calcMoveYaw(yawIn: Float = player.moveYaw, moveForward: Float = roundedForward, moveStrafe: Float = roundedStrafing): Double {
        var strafe = 90 * moveStrafe
        strafe *= if (moveForward != 0F) moveForward * 0.5F else 1F

        var yaw = yawIn - strafe
        yaw -= if (moveForward < 0F) 180 else 0

        return yaw.toDouble()
    }

    fun SafeContext.calcMoveRad() = calcMoveYaw().toRadian()

    fun randomDirection() = random(-180.0, 180.0).toRadian()

    fun SafeContext.movementDirection(radDir: Double = calcMoveRad()) =
        Vec3d(-sin(radDir), 0.0, cos(radDir))

    var Entity.motionX get() = velocity.x; set(value) = setVelocity(value, velocity.y, velocity.z)
    var Entity.motionY get() = velocity.y; set(value) = setVelocity(velocity.x, value, velocity.z)
    var Entity.motionZ get() = velocity.z; set(value) = setVelocity(velocity.x, velocity.y, value)

    fun SafeContext.setSpeed(speed: Double, direction: Double = calcMoveRad()) {
        player.motionX = -sin(direction) * speed
        player.motionZ = cos(direction) * speed
    }

    fun SafeContext.addSpeed(speed: Double, direction: Double = calcMoveRad()) {
        player.motionX -= sin(direction) * speed
        player.motionZ += cos(direction) * speed
    }

    fun SafeContext.mulSpeed(modifier: Double) {
        player.motionX *= modifier
        player.motionZ *= modifier
    }

    val ClientPlayerEntity.moveYaw get() = RotationManager.movementYaw ?: yaw

    val Entity.moveDiff get() = Vec3d(this.pos.x - this.prevX, this.pos.y - this.prevY, this.pos.z - this.prevZ)
    val Entity.moveDelta get() = moveDiff.let { hypot(it.x, it.z) }
    val Entity.motionDelta get() = hypot(this.velocity.x, this.velocity.z)
}