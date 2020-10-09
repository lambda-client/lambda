package me.zeroeightsix.kami.util

import net.minecraft.client.Minecraft
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object MovementUtils {
    private val mc = Minecraft.getMinecraft()

    /* totally not taken from elytrafly */
    fun getMoveYaw(): Double {
        var strafe = 90 * getRoundedStrafing()
        strafe *= if (mc.player.moveForward != 0F) getRoundedForward() * 0.5F else 1F
        var yaw = mc.player.rotationYaw - strafe
        yaw -= if (mc.player.moveForward < 0F) 180 else 0

        return Math.toRadians(yaw.toDouble())
    }

    private fun getRoundedForward(): Float {
        return getRoundedMovementInput(mc.player.moveForward)
    }

    private fun getRoundedStrafing(): Float {
        return getRoundedMovementInput(mc.player.moveStrafing)
    }

    private fun getRoundedMovementInput(input: Float): Float {
        return when {
            input > 0f -> 1f
            input < 0f -> -1f
            else -> 0f
        }
    }

    val isMoving get() = getSpeed() > 0.0001

    /* triangle math tho */
    fun getSpeed(): Double {
        return hypot(mc.player.motionX, mc.player.motionZ)
    }

    fun setSpeed(speed: Double) {
        mc.player.motionX = -sin(getMoveYaw()) * speed
        mc.player.motionZ = cos(getMoveYaw()) * speed
    }
}