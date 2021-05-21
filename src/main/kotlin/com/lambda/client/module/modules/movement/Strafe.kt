package com.lambda.client.module.modules.movement

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.applySpeedPotionEffects
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.setSpeed
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.settings.KeyBinding
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal object Strafe : Module(
    name = "Strafe",
    category = Category.MOVEMENT,
    description = "Improves control in air",
    modulePriority = 100
) {
    private val airSpeedBoost by setting("Air Speed Boost", 0.029f, 0.01f..0.04f, 0.001f)
    private val timerBoost by setting("Timer Boost", true)
    private val autoJump by setting("Auto Jump", true)
    private val onHoldingSprint by setting("On Holding Sprint", false)
    private val cancelInertia by setting("Cancel Inertia", false)

    private var jumpTicks = 0
    private val strafeTimer = TickTimer(TimeUnit.TICKS)

    init {
        onDisable {
            reset()
        }

        safeListener<PlayerTravelEvent> {
            if (!shouldStrafe()) return@safeListener

            player.jumpMovementFactor = airSpeedBoost
            if (timerBoost) modifyTimer(45.87155914306640625f)
            if (Step.isDisabled || !player.collidedHorizontally) {
                if (autoJump) jump()
            }

            strafeTimer.reset()
        }

        safeListener<PlayerMoveEvent> {
            if (!shouldStrafe()) {
                reset()
                if (cancelInertia && !strafeTimer.tick(2L, false)) {
                    player.motionX = 0.0
                    player.motionZ = 0.0
                }
            } else {
                setSpeed(max(player.speed, applySpeedPotionEffects(0.2873)))
            }
        }
    }

    private fun reset() {
        mc.player?.jumpMovementFactor = 0.02f
        resetTimer()
        jumpTicks = 0
    }

    private fun SafeClientEvent.shouldStrafe(): Boolean {
        return (!player.capabilities.isFlying
            && !player.isElytraFlying
            && !mc.gameSettings.keyBindSneak.isKeyDown
            && (!onHoldingSprint || mc.gameSettings.keyBindSprint.isKeyDown)
            && !BaritoneUtils.isPathing
            && MovementUtils.isInputting
            && !(player.isInOrAboveLiquid || player.isInWeb))
    }

    private fun SafeClientEvent.jump() {
        if (player.onGround && jumpTicks <= 0) {
            if (player.isSprinting) {
                val yaw = calcMoveYaw()
                player.motionX -= sin(yaw) * 0.2
                player.motionZ += cos(yaw) * 0.2
            }

            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, false)
            player.motionY = 0.4
            player.isAirBorne = true
            jumpTicks = 5
        }

        jumpTicks--
    }
}
