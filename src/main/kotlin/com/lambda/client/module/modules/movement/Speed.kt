package com.lambda.client.module.modules.movement

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.applySpeedPotionEffects
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.setSpeed
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.settings.KeyBinding
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object Speed : Module(
    name = "Speed",
    description = "Move faster",
    category = Category.MOVEMENT,
    modulePriority = 100
) {
    // General settings
    val mode by setting("Mode", SpeedMode.STRAFE)

    // Strafe settings
    private val strafeAirSpeedBoost by setting("Air Speed Boost", 0.028f, 0.01f..0.04f, 0.001f, { mode == SpeedMode.STRAFE })
    private val strafeTimerBoost by setting("Timer Boost", true, { mode == SpeedMode.STRAFE })
    private val strafeAutoJump by setting("Auto Jump", true, { mode == SpeedMode.STRAFE })
    private val strafeOnHoldingSprint by setting("On Holding Sprint", false, { mode == SpeedMode.STRAFE })
    private val strafeCancelInertia by setting("Cancel Inertia", false, { mode == SpeedMode.STRAFE })

    // YPort settings
    private val yPortAccelerate by setting("Accelerate", true, { mode == SpeedMode.YPORT })
    private val yPortStrict by setting("Head Strict", false, { mode == SpeedMode.YPORT }, description = "Only allow YPort when you are under a block")
    private val yPortAirStrict by setting("Air Strict", false, { mode == SpeedMode.YPORT }, description = "Force YPort to handle Y movement differently, slows this down A LOT")
    private val yPortMaxSpeed by setting("Maximum Speed", 0.0, 0.0..2.0, 0.001, { mode == SpeedMode.YPORT })
    private val yPortCustomValues by setting("Custom Values", false, { mode == SpeedMode.YPORT })
    private val yPortAcceleration by setting("Acceleration Speed", 2.149, 1.0..5.0, 0.001, { mode == SpeedMode.YPORT && yPortCustomValues })
    private val yPortDecay by setting("Decay Amount", 0.66, 0.0..1.0, 0.001, { mode == SpeedMode.YPORT && yPortCustomValues })


    // Strafe Mode
    private var jumpTicks = 0
    private val strafeTimer = TickTimer(TimeUnit.TICKS)

    private var currentMode = mode

    // yport stuff
    private var currentSpeed = .2873
    private var currentY = 0.0
    private var phase = 1
    private var prevPhase = 1
    private var goUp = false
    private var lastDistance = 0.0

    enum class SpeedMode(override val displayName: String) : DisplayEnum {
        STRAFE("Strafe"),
        YPORT("YPort")
    }

    init {
        onEnable {
            currentSpeed = .2873
            phase = 1
            prevPhase = 1
            goUp = false
            currentY = 0.0
        }

        onDisable {
            runSafe {
                reset()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mode != currentMode) {
                currentMode = mode
                reset()
            }

            lastDistance = hypot(player.posX - player.prevPosX, player.posZ - player.prevPosZ)
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
                SpeedMode.YPORT -> {

                    handleBoost(it)

                }
            }
        }

        safeListener<PacketEvent.Send> {

            if (mode == SpeedMode.YPORT
                && it.packet is CPacketPlayer
                // phase is set to 3 in phase 2, so we are detecting when our speed is increased
                && goUp) {


                var offset = (
                    if (world.getBlockState(player.flooredPosition.add(.0, 2.0, .0)).isFullBlock)
                        .2
                    else
                        .42
                    )

                val unModOffset = offset

                if (currentY + unModOffset > 0)
                    offset += currentY
                else if (yPortAirStrict && phase == 4 && prevPhase == 4) {

                    var predictedY = currentY
                    predictedY -= 0.08
                    predictedY *= 0.9800000190734863 // 0.333200006 vs 0.341599999

                    if (predictedY + player.posY <= player.posY) {
                        phase = 0
                    }

                }

                it.packet.playerY = (offset + player.posY)

                currentY = offset - unModOffset

            }

        }

        safeListener<PacketEvent.Receive> {

            if (mode == SpeedMode.YPORT)
                if (it.packet is SPacketPlayerPosLook) {

                    currentSpeed = 0.0
                    currentY = 0.0
                    goUp = false
                    // 3 extra ticks at base speed
                    phase = -3

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

    private fun SafeClientEvent.handleBoost(event: PlayerMoveEvent) {

        if (player.movementInput.moveForward == 0f && player.movementInput.moveStrafe == 0f || player.isInOrAboveLiquid || mc.gameSettings.keyBindJump.isKeyDown
            || !player.onGround || !world.collidesWithAnyBlock(player.entityBoundingBox.offset(0.0, 0.42, 0.0)) && yPortStrict) {
            resetTimer()
            currentSpeed = .2873
            return
        }

        modifyTimer(45.955883f)

        when (phase) {

            2 -> {
                prevPhase = 2

                // NCP says hDistance < 2.15 * hDistanceBaseRef
                currentSpeed *= if (yPortCustomValues) yPortAcceleration else 2.149
                phase = if (yPortAirStrict) 4 else 3
                goUp = true
                currentY = 0.0
            }

            3 -> {
                prevPhase = 3

                // NCP says hDistDiff >= 0.66 * (lastMove.hDistance - hDistanceBaseRef)
                currentSpeed = if (yPortAccelerate) {
                    lastDistance - (if (yPortCustomValues) yPortDecay else .66) * (lastDistance - .2873)
                } else {
                    .2873
                }
                phase = 2
                goUp = false
            }

            4 -> {

                if (prevPhase == 2)
                    currentSpeed = if (yPortAccelerate) {
                        lastDistance - (if (yPortCustomValues) yPortDecay else .66) * (lastDistance - .2873)
                    } else {
                        .2873
                    }

                prevPhase = 4

                goUp = true

                currentSpeed -= currentSpeed / 159

                currentY -= 0.08
                currentY *= 0.9800000190734863

            }

            else -> {
                prevPhase = 1

                currentSpeed = max(currentSpeed, .2873)
                phase++
                goUp = false
            }
        }

        val yaw = calcMoveYaw()

        if (yPortMaxSpeed != 0.0)
            currentSpeed = currentSpeed.coerceAtMost(yPortMaxSpeed)

        event.x = -sin(yaw) * currentSpeed
        event.y = min(0.0, event.y)
        event.z = cos(yaw) * currentSpeed

        player.setVelocity(event.x, event.y, event.z)

    }

}
