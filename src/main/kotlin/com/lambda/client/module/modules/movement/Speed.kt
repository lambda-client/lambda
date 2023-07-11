package com.lambda.client.module.modules.movement

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.applySpeedPotionEffects
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
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
    val mode = setting("Mode", SpeedMode.STRAFE)

    // Strafe settings
    private val strafeAirSpeedBoost by setting("Strafe Speed", StrafeMode.Normal)
    private val strafeOnlyOverhead by setting("Require Roof", false, { mode.value == SpeedMode.STRAFE })
    private val strafeOnHoldingSprint by setting("On Holding Sprint", false, { mode.value == SpeedMode.STRAFE })

    // YPort settings
    private val yPortAccelerate by setting("Accelerate", true, { mode.value == SpeedMode.YPORT })
    private val yPortStrict by setting("Head Strict", false, { mode.value == SpeedMode.YPORT }, description = "Only allow YPort when you are under a block")
    private val yPortAirStrict by setting("Air Strict", false, { mode.value == SpeedMode.YPORT }, description = "Force YPort to handle Y movement differently, slows this down A LOT")
    private val yPortMaxSpeed by setting("Maximum Speed", 0.0, 0.0..2.0, 0.001, { mode.value == SpeedMode.YPORT })
    private val yPortAcceleration by setting("Acceleration Speed", 2.149, 1.0..5.0, 0.001, { mode.value == SpeedMode.YPORT })
    private val yPortDecay by setting("Decay Amount", 0.66, 0.0..1.0, 0.001, { mode.value == SpeedMode.YPORT })

    private const val TIMER_SPEED = 45.922115f

    // yport stuff
    private var currentSpeed = .2873
    private var currentY = 0.0

    private var strafePhase = StrafePhase.ACCELERATING

    private var yPortPhase = YPortPhase.WALKING
    private var prevYPortPhase = YPortPhase.WALKING

    private var goUp = false
    private var lastDistance = 0.0

    private enum class YPortPhase {
        // to help with bypassing right after setback
        WAITING,
        // to get some speed initially
        WALKING,
        // to jump and accelerate
        ACCELERATING,
        // to fall to the ground
        SLOWDOWN,
        // to slowly fall to the ground
        FALLING
    }

    private enum class StrafePhase {
        // to jump and accelerate
        ACCELERATING,
        // to fall to the ground
        SLOWDOWN,
        // to slowly fall to the ground
        FALLING
    }

    enum class SpeedMode(override val displayName: String) : DisplayEnum {
        STRAFE("Strafe"),
        YPORT("YPort")
    }

    enum class StrafeMode {
        Normal, Strict
    }

    init {
        onEnable {
            currentSpeed = .2873
            strafePhase = StrafePhase.ACCELERATING
            yPortPhase = YPortPhase.WALKING
            prevYPortPhase = YPortPhase.WALKING
            goUp = false
            currentY = 0.0
        }

        onDisable {
            runSafe {
                reset()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            lastDistance = hypot(player.posX - player.prevPosX, player.posZ - player.prevPosZ)
        }

        safeListener<PlayerMoveEvent> {
            when (mode.value) {
                SpeedMode.STRAFE -> {
                    handleStrafe(it)
                }

                SpeedMode.YPORT -> {
                    handleBoost(it)
                }
            }
        }

        safeListener<PacketEvent.Send> {
            if (mode.value != SpeedMode.YPORT
                || it.packet !is CPacketPlayer
                || !goUp
            ) return@safeListener

            var offset = .42

            //.015625 is the largest number that block heights are always divisible
            while (world.collidesWithAnyBlock(player.entityBoundingBox.offset(.0, offset, .0))) {
                if (offset <= 0)
                    break

                offset -= .015625
            }

            val unModOffset = offset

            if (currentY + unModOffset > 0)
                offset += currentY
            else if (yPortAirStrict && yPortPhase == YPortPhase.FALLING && prevYPortPhase == YPortPhase.FALLING) {

                var predictedY = currentY
                predictedY -= 0.08
                predictedY *= 0.9800000190734863 // 0.333200006 vs 0.341599999

                if (predictedY + player.posY <= player.posY) {
                    yPortPhase = YPortPhase.WAITING
                }
            }

            it.packet.playerY = (offset + player.posY)

            currentY = offset - unModOffset
        }

        safeListener<PacketEvent.Receive> {
            if (mode.value != SpeedMode.YPORT || it.packet !is SPacketPlayerPosLook) return@safeListener

            currentSpeed = 0.0
            currentY = 0.0
            goUp = false
            // 3 extra ticks at base speed
            yPortPhase = YPortPhase.WAITING
        }

        mode.listeners.add {
            runSafe { reset() }
        }
    }

    private fun SafeClientEvent.shouldStrafe(): Boolean =
        !player.capabilities.isFlying
            && !player.isElytraFlying
            && !BaritoneUtils.isPathing
            && MovementUtils.isInputting

    private fun SafeClientEvent.reset() {
        player.jumpMovementFactor = 0.02f
        resetTimer()
    }

    private fun SafeClientEvent.handleBoost(event: PlayerMoveEvent) {
        if (player.movementInput.moveForward == 0f && player.movementInput.moveStrafe == 0f
            || player.isInOrAboveLiquid
            || mc.gameSettings.keyBindJump.isKeyDown
            || !player.onGround
            || !world.collidesWithAnyBlock(player.entityBoundingBox.offset(0.0, 0.42, 0.0)) && yPortStrict
        ) {
            resetTimer()
            currentSpeed = .2873
            return
        }

        modifyTimer(TIMER_SPEED)

        prevYPortPhase = yPortPhase

        when (yPortPhase) {
            YPortPhase.ACCELERATING -> {
                // NCP says hDistance < 2.15 * hDistanceBaseRef
                currentSpeed *= yPortAcceleration
                yPortPhase = if (yPortAirStrict) YPortPhase.FALLING else YPortPhase.SLOWDOWN
                goUp = true
                currentY = 0.0
            }

            YPortPhase.SLOWDOWN -> {
                // NCP says hDistDiff >= 0.66 * (lastMove.hDistance - hDistanceBaseRef)
                currentSpeed = if (yPortAccelerate) {
                    lastDistance - yPortDecay * (lastDistance - .2873)
                } else {
                    .2873
                }
                yPortPhase = YPortPhase.ACCELERATING
                goUp = false
            }

            YPortPhase.FALLING -> {
                if (prevYPortPhase == YPortPhase.WALKING) {
                    currentSpeed = if (yPortAccelerate) {
                        lastDistance - yPortDecay * (lastDistance - .2873)
                    } else {
                        .2873
                    }
                }

                goUp = true

                currentSpeed -= currentSpeed / 159

                currentY -= 0.08
                currentY *= 0.9800000190734863
            }

            else -> {
                currentSpeed = max(currentSpeed, .2873)
                yPortPhase = YPortPhase.values()[yPortPhase.ordinal + 1 % YPortPhase.values().size]
                goUp = false
            }
        }

        val yaw = calcMoveYaw()

        if (yPortMaxSpeed != 0.0) {
            currentSpeed = currentSpeed.coerceAtMost(yPortMaxSpeed)
        }

        event.x = -sin(yaw) * currentSpeed
        event.y = min(0.0, event.y)
        event.z = cos(yaw) * currentSpeed

        player.setVelocity(event.x, event.y, event.z)
    }

    private fun SafeClientEvent.handleStrafe(event: PlayerMoveEvent) {
        if (!shouldStrafe()) {
            resetTimer()
            event.x = .0
            event.z = .0
            currentSpeed = .2873
            return
        }

        if (strafeOnlyOverhead && !world.collidesWithAnyBlock(player.entityBoundingBox.offset(.0,.42,.0))
            || strafeOnHoldingSprint && !mc.gameSettings.keyBindSprint.isKeyDown)
            return

        modifyTimer(TIMER_SPEED)

        val base = applySpeedPotionEffects(.2873)

        if (player.onGround)
            strafePhase = StrafePhase.ACCELERATING

        when (strafePhase) {
            StrafePhase.ACCELERATING -> {
                if (player.onGround)
                    event.y = .42
                currentSpeed = base
                currentSpeed *= if (strafeAirSpeedBoost == StrafeMode.Strict) 1.87 else 1.93
                strafePhase = StrafePhase.SLOWDOWN
            }

            StrafePhase.SLOWDOWN -> {
                currentSpeed -= .66 * base
                strafePhase = StrafePhase.FALLING
            }

            StrafePhase.FALLING -> {
                currentSpeed = lastDistance - lastDistance / 159
            }
        }

        val yaw = calcMoveYaw()
        currentSpeed = currentSpeed.coerceAtLeast(.2873)
        event.x = -sin(yaw) * currentSpeed
        event.z = cos(yaw) * currentSpeed
    }
}
