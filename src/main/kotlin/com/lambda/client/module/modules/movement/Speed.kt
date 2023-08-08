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
import com.lambda.client.util.MovementUtils.calcMoveYaw
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
    private val mode by setting("Mode", Mode.STRAFE).apply {
        listeners.add {
            resetTimer()
        }
    }

    // Strafe settings
    private val strafeBaseSpeed by setting("Base Speed", 0.2873, 0.1..0.3, 0.0001, { mode == Mode.STRAFE })
    private val strafeMaxSpeed by setting("Max Speed", 1.0, 0.3..1.0, 0.0001, { mode == Mode.STRAFE })
    private val strafeDecay by setting("Strafe Decay", 0.9937, 0.9..1.0, 0.0001, { mode == Mode.STRAFE })
    private val strafeJumpSpeed by setting("Jump Speed", 0.3, 0.0..1.0, 0.0001, { mode == Mode.STRAFE })
    private val strafeJumpHeight by setting("Jump Height", 0.42, 0.1..0.5, 0.0001, { mode == Mode.STRAFE })
    private val strafeJumpDecay by setting("Jump Decay", 0.59, 0.1..1.0, 0.0001, { mode == Mode.STRAFE })
    private val strafeResetOnJump by setting("Reset On Jump", true, { mode == Mode.STRAFE })
    private val strafeTimer by setting("Strafe Timer", 1.09f, 1.0f..1.1f, 0.01f, { mode == Mode.STRAFE })
    private val strafeAutoJump by setting("Auto Jump", false, { mode == Mode.STRAFE })

    // YPort settings
    private val yPortAccelerate by setting("Accelerate", true, { mode == Mode.Y_PORT })
    private val yPortStrict by setting("Head Strict", false, { mode == Mode.Y_PORT }, description = "Only allow YPort when you are under a block")
    private val yPortAirStrict by setting("Air Strict", false, { mode == Mode.Y_PORT }, description = "Force YPort to handle Y movement differently, slows this down A LOT")
    private val yPortMaxSpeed by setting("Maximum Speed", 0.0, 0.0..2.0, 0.001, { mode == Mode.Y_PORT })
    private val yPortAcceleration by setting("Acceleration Speed", 2.149, 1.0..5.0, 0.001, { mode == Mode.Y_PORT })
    private val yPortDecay by setting("YPort Decay", 0.66, 0.0..1.0, 0.001, { mode == Mode.Y_PORT })
    private val yPortTimer by setting("YPort Timer", 1.09f, 1.0f..1.1f, 0.01f, { mode == Mode.Y_PORT })

    private const val NCP_BASE_SPEED = 0.2873

    // yport stuff
    private var currentSpeed = NCP_BASE_SPEED
    private var currentY = 0.0

    private var strafePhase = StrafePhase.FALLING

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
        // to jump
        JUMP,
        // to slowdown on the next tick after jump
        JUMP_SLOWDOWN,
        // to fall to the ground
        FALLING
    }

    private enum class Mode(override val displayName: String, val move: SafeClientEvent.(e: PlayerMoveEvent) -> Unit) : DisplayEnum {
        STRAFE("Strafe", { handleStrafe(it) }),
        Y_PORT("YPort", { handleBoost(it) }),
    }

    init {
        onEnable {
            currentSpeed = if (mode == Mode.Y_PORT) NCP_BASE_SPEED else strafeBaseSpeed
            strafePhase = StrafePhase.FALLING
            yPortPhase = YPortPhase.WALKING
            prevYPortPhase = YPortPhase.WALKING
            goUp = false
            currentY = 0.0
        }

        onDisable {
            resetTimer()
        }

        safeListener<TickEvent.ClientTickEvent> {
            lastDistance = hypot(player.posX - player.prevPosX, player.posZ - player.prevPosZ)
        }

        safeListener<PlayerMoveEvent> { event ->
            mode.move(this, event)
        }

        safeListener<PacketEvent.Send> {
            if (mode != Mode.Y_PORT
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

            if (currentY + unModOffset > 0) {
                offset += currentY
            } else if (yPortAirStrict && yPortPhase == YPortPhase.FALLING && prevYPortPhase == YPortPhase.FALLING) {
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
            if (mode != Mode.Y_PORT || it.packet !is SPacketPlayerPosLook) return@safeListener

            currentSpeed = 0.0
            currentY = 0.0
            goUp = false
            // 3 extra ticks at base speed
            yPortPhase = YPortPhase.WAITING
        }
    }

    private fun SafeClientEvent.handleBoost(event: PlayerMoveEvent) {
        if (!MovementUtils.isInputting
            || player.isInOrAboveLiquid
            || mc.gameSettings.keyBindJump.isKeyDown
            || !player.onGround
            || !world.collidesWithAnyBlock(player.entityBoundingBox.offset(0.0, 0.42, 0.0)) && yPortStrict
        ) {
            resetTimer()
            currentSpeed = NCP_BASE_SPEED
            return
        }

        modifyTimer(50f / yPortTimer)

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
                    lastDistance - yPortDecay * (lastDistance - NCP_BASE_SPEED)
                } else {
                    NCP_BASE_SPEED
                }
                yPortPhase = YPortPhase.ACCELERATING
                goUp = false
            }

            YPortPhase.FALLING -> {
                if (prevYPortPhase == YPortPhase.WALKING) {
                    currentSpeed = if (yPortAccelerate) {
                        lastDistance - yPortDecay * (lastDistance - NCP_BASE_SPEED)
                    } else {
                        NCP_BASE_SPEED
                    }
                }

                goUp = true

                currentSpeed -= currentSpeed / 159

                currentY -= 0.08
                currentY *= 0.9800000190734863
            }

            else -> {
                currentSpeed = max(currentSpeed, NCP_BASE_SPEED)
                yPortPhase = YPortPhase.entries.toTypedArray()[yPortPhase.ordinal + 1 % YPortPhase.entries.size]
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
        val inputting = MovementUtils.isInputting

        if (player.capabilities.isFlying
            || player.isElytraFlying
            || BaritoneUtils.isPathing
        ) {
            currentSpeed = strafeBaseSpeed
            resetTimer()
            return
        }

        modifyTimer(50f / strafeTimer)

        val shouldJump = player.movementInput.jump || (inputting && strafeAutoJump)

        if (player.onGround && shouldJump) {
            strafePhase = StrafePhase.JUMP
        }

        strafePhase = when (strafePhase) {
            StrafePhase.JUMP -> {
                if (player.onGround) {
                    event.y = strafeJumpHeight

                    if (strafeResetOnJump) currentSpeed = strafeBaseSpeed
                    currentSpeed += strafeJumpSpeed

                    StrafePhase.JUMP_SLOWDOWN
                } else StrafePhase.FALLING
            }

            StrafePhase.JUMP_SLOWDOWN -> {
                currentSpeed *= strafeJumpDecay
                StrafePhase.FALLING
            }

            StrafePhase.FALLING -> {
                currentSpeed = lastDistance * strafeDecay
                StrafePhase.FALLING
            }
        }

        if (player.onGround && !shouldJump) {
            currentSpeed = strafeBaseSpeed
        }

        currentSpeed = currentSpeed.coerceAtLeast(strafeBaseSpeed).coerceAtMost(strafeMaxSpeed)

        val moveSpeed = if (!inputting) {
            currentSpeed = strafeBaseSpeed
            resetTimer()

            0.0
        } else currentSpeed

        val dir = calcMoveYaw()
        event.x = -sin(dir) * moveSpeed
        event.z = cos(dir) * moveSpeed
    }

    // For HoleSnap & Surround
    fun isStrafing() = mode == Mode.STRAFE
}
