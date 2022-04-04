package com.lambda.client.module.modules.movement

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.*
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
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.cos
import kotlin.math.sin

object Speed : Module(
    name = "Speed",
    description = "Move faster",
    category = Category.MOVEMENT,
    modulePriority = 100
) {
    // General settings
    val mode by setting("Mode", SpeedMode.STRAFE)

    // strafe settings
    private val strafeAirSpeedBoost by setting("Air Speed Boost", 0.028f, 0.01f..0.04f, 0.001f, { mode == SpeedMode.STRAFE })
    private val strafeTimerBoost by setting("Timer Boost", true, { mode == SpeedMode.STRAFE })
    private val strafeAutoJump by setting("Auto Jump", true, { mode == SpeedMode.STRAFE })
    private val strafeOnHoldingSprint by setting("On Holding Sprint", false, { mode == SpeedMode.STRAFE })
    private val strafeCancelInertia by setting("Cancel Inertia", false, { mode == SpeedMode.STRAFE })

    // onGround settings
    private val onGroundTimer by setting("Timer", true, { mode == SpeedMode.ONGROUND })
    private val onGroundTimerSpeed by setting("Timer Speed", 1.088f, 1.0f..2.0f, 0.01f, { mode == SpeedMode.ONGROUND && onGroundTimer })
    private val onGroundSpeed by setting("Speed", 1.31f, 1.0f..2.0f, 0.01f, { mode == SpeedMode.ONGROUND })
    private val onGroundSprint by setting("Sprint", true, { mode == SpeedMode.ONGROUND })
    private val onGroundCheckAbove by setting("Smart Mode", true, { mode == SpeedMode.ONGROUND })

    // boost settings
    private val boostSpeed by setting("Boost Speed", .388, 0.28..1.0, 0.01, {mode == SpeedMode.BOOST})

    // Strafe Mode
    private var jumpTicks = 0
    private val strafeTimer = TickTimer(TimeUnit.TICKS)

    // onGround Mode
    private var wasSprintEnabled = Sprint.isEnabled

    private var currentMode = mode

    private var spoofUp = true

    enum class SpeedMode {
        STRAFE, ONGROUND, BOOST
    }

    init {
        onEnable {
            wasSprintEnabled = Sprint.isEnabled
        }

        onDisable {
            if (!wasSprintEnabled && mode == SpeedMode.ONGROUND) Sprint.disable()
            runSafe {
                reset()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mode != currentMode) {
                currentMode = mode
                reset()
            }

            if (mode == SpeedMode.ONGROUND && Sprint.isDisabled && onGroundSprint) Sprint.enable()
        }

        safeListener<PlayerTravelEvent> {
            if (mode == SpeedMode.STRAFE && shouldStrafe()) strafe()
        }

        safeListener<PlayerMoveEvent> {
            when (mode) {
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
                SpeedMode.ONGROUND -> {
                    if (shouldOnGround()) onGround()
                    else resetTimer()
                }
                SpeedMode.BOOST -> {

                    handleBoost(it)

                }
            }
        }

        safeListener<PacketEvent.Send> {

            if (mode == SpeedMode.BOOST) {

                if (it.packet is CPacketPlayer) {

                    if (it.packet.playerMoving && spoofUp) {

                        it.packet.playerIsOnGround = false

                        val pos =
                            (
                                if (
                                    world.getBlockState(player.flooredPosition.add(0.0, 2.0, 0.0)).material.isSolid
                                )
                                    .2
                                else
                                    .42
                                ) + player.posY

                        it.packet.playerY = pos

                    }
                }

            }

        }

    }

    private fun SafeClientEvent.strafe() {
        player.jumpMovementFactor = strafeAirSpeedBoost
        // slightly slower timer speed bypasses better (1.088)
        if (strafeTimerBoost) modifyTimer(45.955883f)
        if ((Step.isDisabled || !player.collidedHorizontally) && strafeAutoJump) jump()

        strafeTimer.reset()
    }

    private fun SafeClientEvent.onGround() {
        if (onGroundTimer) modifyTimer(50.0f / onGroundTimerSpeed)
        else resetTimer()

        player.motionX *= onGroundSpeed
        player.motionZ *= onGroundSpeed
    }

    private fun SafeClientEvent.shouldStrafe(): Boolean =
        (!player.capabilities.isFlying
            && !player.isElytraFlying
            && !mc.gameSettings.keyBindSneak.isKeyDown
            && (!strafeOnHoldingSprint || mc.gameSettings.keyBindSprint.isKeyDown)
            && !BaritoneUtils.isPathing
            && MovementUtils.isInputting
            && !(player.isInOrAboveLiquid || player.isInWeb))

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

    private fun SafeClientEvent.handleBoost(event : PlayerMoveEvent) {

        spoofUp = !spoofUp && player.onGround

        if (player.movementInput.moveForward == 0f && player.movementInput.moveStrafe == 0f || player.isInOrAboveLiquid || mc.gameSettings.keyBindJump.isKeyDown) {
            modifyTimer(50f)
            spoofUp = false
            return
        }

        modifyTimer(45.955883f)

        val speed = if (spoofUp) boostSpeed else .2873

        val yaw = calcMoveYaw()
        event.x = -sin(yaw) * speed
        if (spoofUp) {
            event.y = min(0.0, event.y)
        }
        event.z = cos(yaw) * speed

        player.setVelocity(event.x,event.y,event.z)

    }

}
