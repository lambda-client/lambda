package com.lambda.client.module.modules.movement

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.mixin.extension.tickLength
import com.lambda.client.mixin.extension.timer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.AutoEat
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.applySpeedPotionEffects
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.MovementUtils.setSpeed
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.Double.max
import kotlin.math.cos
import kotlin.math.sin

object Speed : Module(
    name = "Speed",
    description = "vrooommm",
    category = Category.MOVEMENT,
) {
    // General settings
    val mode by setting("Mode", SpeedMode.STRAFE)

    // strafe settings
    private val strafeAirSpeedBoost by setting("Air Speed Boost", 0.029f, 0.01f..0.04f, 0.001f, { mode == SpeedMode.STRAFE })
    private val strafeTimerBoost by setting("Timer Boost", true, { mode == SpeedMode.STRAFE })
    private val strafeAutoJump by setting("Auto Jump", true, { mode == SpeedMode.STRAFE })
    private val strafeOnHoldingSprint by setting("On Holding Sprint", false, { mode == SpeedMode.STRAFE })
    private val strafeCancelInertia by setting("Cancel Inertia", false, { mode == SpeedMode.STRAFE })

    // onGround settings
    private val onGroundTimer by setting("Timer", true, { mode == SpeedMode.ONGROUND })
    private val onGroundTimerSpeed by setting("Timer Speed", 1.29f, 1.0f..2.0f, 0.01f, { mode == SpeedMode.ONGROUND && onGroundTimer })
    private val onGroundSpeed by setting("Speed", 1.31f, 1.0f..2.0f, 0.01f, { mode == SpeedMode.ONGROUND })
    private val onGroundSprint by setting("Sprint", true, { mode == SpeedMode.ONGROUND })
    private val onGroundCheckAbove by setting("Smart Mode", true, { mode == SpeedMode.ONGROUND })

    // Strafe Mode
    private var jumpTicks = 0
    private val strafeTimer = TickTimer(TimeUnit.TICKS)

    // onGround Mode
    private var wasSprintEnabled = Sprint.isEnabled

    private var currentMode = mode

    enum class SpeedMode {
        ONGROUND, STRAFE
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (isDisabled) return@safeListener

            if (mode != currentMode) {
                currentMode = mode
                reset()
            }

            if (mode == SpeedMode.ONGROUND && Sprint.isDisabled && onGroundSprint) Sprint.enable()
        }

        safeListener<PlayerTravelEvent> {
            when (mode) {
                SpeedMode.STRAFE -> {
                    if (shouldStrafe()) strafe()
                }
                SpeedMode.ONGROUND -> {
                }
            }
        }

        safeListener<PlayerMoveEvent> {
            if (isDisabled) return@safeListener

            when (mode) {
                SpeedMode.ONGROUND -> {
                    if (shouldOnGround()) onGround()
                    else mc.timer.tickLength = 50.0f
                }
                SpeedMode.STRAFE -> {
                    if (shouldStrafe()) setSpeed(max(player.speed, applySpeedPotionEffects(0.2873)))
                    else {
                        reset()
                        if (strafeCancelInertia && !strafeTimer.tick(2L, false)) {
                            player.motionX = 0.0
                            player.motionZ = 0.0
                        }
                    }
                }
            }

            onEnable {
                wasSprintEnabled = Sprint.isEnabled
            }

            onDisable {
                if (!wasSprintEnabled && mode == SpeedMode.ONGROUND) Sprint.disable()
                runSafe {
                    reset()
                }
            }
        }
    }

    private fun SafeClientEvent.onGround() {
        if (onGroundTimer) mc.timer.tickLength = 50.0f / onGroundTimerSpeed
        else mc.timer.tickLength = 50.0f

        player.motionX *= onGroundSpeed
        player.motionZ *= onGroundSpeed
    }

    private fun SafeClientEvent.strafe() {
        player.jumpMovementFactor = strafeAirSpeedBoost
        if (strafeTimerBoost) modifyTimer(45.87155914306640625f)
        if ((Step.isDisabled || !player.collidedHorizontally) && strafeAutoJump) jump()

        strafeTimer.reset()
    }

    private fun SafeClientEvent.shouldOnGround(): Boolean =
        (world.getBlockState(player.flooredPosition.add(0.0, 2.0, 0.0)).material.isSolid || !onGroundCheckAbove)
            && !AutoEat.eating
            && player.isMoving
            && MovementUtils.isInputting
            && !player.movementInput.sneak
            && player.onGround
            && !(player.isInOrAboveLiquid || player.isInWeb)
            && !player.capabilities.isFlying
            && !player.isElytraFlying
            && !mc.gameSettings.keyBindSneak.isKeyDown

    private fun SafeClientEvent.shouldStrafe(): Boolean =
        (!player.capabilities.isFlying
            && !player.isElytraFlying
            && !mc.gameSettings.keyBindSneak.isKeyDown
            && (!strafeOnHoldingSprint || mc.gameSettings.keyBindSprint.isKeyDown)
            && !BaritoneUtils.isPathing
            && MovementUtils.isInputting
            && !(player.isInOrAboveLiquid || player.isInWeb))

    private fun SafeClientEvent.reset() {
        player.jumpMovementFactor = 0.02f
        resetTimer()
        jumpTicks = 0
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