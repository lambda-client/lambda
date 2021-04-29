package com.lambda.client.module.modules.movement

import com.lambda.client.event.SafeClientEvent
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
import kotlin.math.sin

internal object Strafe : Module(
    name = "Strafe",
    category = Category.MOVEMENT,
    description = "Improves control in air",
    modulePriority = 100
) {
    private val mode by setting("Mode", SpeedBoost.NCP)
    private val page by setting("Page", Page.GENERIC_SETTINGS)

    /* Generic Settings */
    private val airSpeedBoost by setting("Air Speed Boost", true, { page == Page.GENERIC_SETTINGS })
    private val groundSpeedBoost by setting("Ground Speed Boost", true, { page == Page.GENERIC_SETTINGS })
    private val timerBoost by setting("Timer Boost", true, { page == Page.GENERIC_SETTINGS })
    private val autoJump by setting("Auto Jump", true, { page == Page.GENERIC_SETTINGS })
    private val onHoldingSprint by setting("On Holding Sprint", false, { page == Page.GENERIC_SETTINGS })
    private val cancelInertia by setting("Cancel Inertia", false, { page == Page.GENERIC_SETTINGS })

    /* NCP Mode */
    private val ncpStrict by setting("NCP Strict", false, { mode == SpeedBoost.NCP && page == Page.MODE_SETTINGS })

    /* Custom Mode */
    private val settingSpeed by setting("Speed", 0.28, 0.0..1.0, 0.01, { mode == SpeedBoost.CUSTOM && page == Page.MODE_SETTINGS })
    private val constantSpeed by setting("Constant Speed", false, { mode == SpeedBoost.CUSTOM && page == Page.MODE_SETTINGS })

    private enum class SpeedBoost {
        NCP, CUSTOM
    }

    private enum class Page {
        GENERIC_SETTINGS, MODE_SETTINGS
    }

    private var jumpTicks = 0
    private val strafeTimer = TickTimer(TimeUnit.TICKS)

    init {
        onDisable {
            reset()
        }

        safeListener<PlayerTravelEvent> {
            if (!shouldStrafe) {
                reset()
                if (cancelInertia && !strafeTimer.tick(2L, false)) {
                    player.motionX = 0.0
                    player.motionZ = 0.0
                }
                return@safeListener
            }


            if (airSpeedBoost) player.jumpMovementFactor = 0.029f
            if (timerBoost) modifyTimer(45.87155914306640625f)
            if (!player.collidedHorizontally) {
                if (autoJump) jump()
                setSpeed(getSpeed())
            }

            strafeTimer.reset()
        }
    }

    private fun reset() {
        mc.player?.jumpMovementFactor = 0.02f
        resetTimer()
        jumpTicks = 0
    }

    private val SafeClientEvent.shouldStrafe: Boolean
        get() = !player.capabilities.isFlying
            && !player.isElytraFlying
            && !mc.gameSettings.keyBindSneak.isKeyDown
            && (!onHoldingSprint || mc.gameSettings.keyBindSprint.isKeyDown)
            && !BaritoneUtils.isPathing
            && MovementUtils.isInputting
            && !(player.isInOrAboveLiquid || player.isInWeb)

    private fun SafeClientEvent.jump() {
        if (player.onGround && jumpTicks <= 0) {
            if (player.isSprinting) {
                val yaw = calcMoveYaw()
                player.motionX -= sin(yaw) * 0.2
                player.motionZ += cos(yaw) * 0.2
            }

            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, false)
            player.motionY = 0.41
            player.isAirBorne = true
            jumpTicks = 5
        }

        jumpTicks--
    }

    private fun SafeClientEvent.getSpeed() = when (mode) {
        SpeedBoost.NCP -> {
            if (shouldBoostGroundSpeed) {
                val speed = if (ncpStrict) 0.26 else 0.28
                applySpeedPotionEffects(speed)
            } else {
                player.speed
            }
        }
        SpeedBoost.CUSTOM -> {
            when {
                constantSpeed -> {
                    settingSpeed
                }
                shouldBoostGroundSpeed -> {
                    applySpeedPotionEffects(settingSpeed)
                }
                else -> {
                    player.speed
                }
            }
        }
    }

    private val SafeClientEvent.shouldBoostGroundSpeed
        get() = groundSpeedBoost && player.onGround
}
