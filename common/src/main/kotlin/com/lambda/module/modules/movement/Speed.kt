package com.lambda.module.modules.movement

import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.MovementUtils.setSpeed
import kotlin.math.atan2

object Speed : Module(
    name = "Speed",
    description = "Accelerates your walking speed",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    @JvmStatic val mode by setting("Mode", Mode.GRIM_STRAFE)

    // Grim
    private val rotation = RotationSettings(this)

    // NCP
    private val ncpBaseSpeed by setting("Base Speed", 0.2873, 0.1..0.3, 0.0001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpMaxSpeed by setting("Max Speed", 1.0, 0.3..1.0, 0.0001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpDecay by setting("Decay", 0.9937, 0.98..1.0, 0.0001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpJumpSpeed by setting("Jump Speed", 0.3, 0.0..0.5, 0.001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpJumpDecay by setting("Jump Decay", 0.59, 0.1..1.0, 0.0001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpJumpHeight by setting("Jump Height", 0.4, 0.1..0.5, 0.00001, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpResetOnJump by setting("Reset On Jump", true, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpAutoJump by setting("Auto Jump", false, visibility = { mode == Mode.NCP_STRAFE })
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01, visibility = { mode == Mode.NCP_STRAFE })

    // Grim
    private var desiredRotation = Rotation.ZERO

    // NCP
    private var ncpPhase = NCPPhase.JUMP
    private var ncpSpeed = ncpBaseSpeed
    private var lastDistance = 0.0

    enum class Mode {
        GRIM_STRAFE,
        NCP_STRAFE,
    }

    private enum class NCPPhase {
        JUMP,
        JUMP_POST,
        SLOWDOWN
    }

    init {
        listener<MovementEvent.InputUpdate> {
            it.input.let { input ->
                val dx = if (input.pressingForward) 1 else if (input.pressingBack) -1 else 0
                val dy = if (input.pressingRight) 1 else if (input.pressingLeft) -1 else 0

                desiredRotation = if (dx != 0 || dy != 0) {
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    Rotation(player.yaw + angle.toFloat(), player.pitch)
                } else {
                    Rotation.ZERO
                }
            }
        }

        listener<RotationEvent.Pre> {
            if (!shouldWork()) return@listener
            if (mode != Mode.GRIM_STRAFE) return@listener
            if (desiredRotation == Rotation.ZERO) return@listener

            it.context = RotationContext(
                desiredRotation,
                rotation
            )
        }

        listener<MovementEvent.Pre> {
            if (!shouldWork()) {
                ncpSpeed = ncpBaseSpeed
                return@listener
            }

            when (mode) {
                Mode.NCP_STRAFE -> handleStrafe()
                Mode.GRIM_STRAFE -> handleGrim()
            }
        }

        listener<MovementEvent.Post> {
            lastDistance = player.moveDelta
        }

        listener<ClientEvent.Timer> {
            if (!shouldWork() || !isInputting) return@listener
            if (mode != Mode.NCP_STRAFE) return@listener
            it.speed = ncpTimerBoost
        }

        listener<MovementEvent.Jump> {
            if (!shouldWork()) return@listener

            when (mode) {
                Mode.NCP_STRAFE -> it.cancel()
                Mode.GRIM_STRAFE -> {}
            }
        }

        onEnable {
            ncpPhase = NCPPhase.SLOWDOWN
            ncpSpeed = ncpBaseSpeed
        }
    }

    private fun SafeContext.handleGrim() {

    }

    private fun SafeContext.handleStrafe() {
        val shouldJump = player.input.jumping || (ncpAutoJump && isInputting)

        if (player.isOnGround && shouldJump) {
            ncpPhase = NCPPhase.JUMP
        }

        ncpPhase = when (ncpPhase) {
            NCPPhase.JUMP -> {
                if (player.isOnGround) {
                    if (ncpResetOnJump) ncpSpeed = ncpBaseSpeed

                    player.motionY = ncpJumpHeight
                    ncpSpeed += ncpJumpSpeed
                    NCPPhase.JUMP_POST
                } else NCPPhase.SLOWDOWN
            }

            NCPPhase.JUMP_POST -> {
                ncpSpeed *= ncpJumpDecay
                NCPPhase.SLOWDOWN
            }

            NCPPhase.SLOWDOWN -> {
                ncpSpeed = lastDistance * ncpDecay
                NCPPhase.SLOWDOWN
            }
        }

        if (player.isOnGround && !shouldJump) {
            ncpSpeed = ncpBaseSpeed
        }

        ncpSpeed = ncpSpeed
            .coerceAtMost(ncpMaxSpeed)
            .coerceAtLeast(ncpBaseSpeed)

        val moveSpeed = if (isInputting) ncpSpeed else {
            ncpSpeed = ncpBaseSpeed
            0.0
        }

        setSpeed(moveSpeed)
    }

    private fun SafeContext.shouldWork() =
        !player.abilities.flying
                && !player.isFallFlying
                && !player.input.sneaking
                && !player.isTouchingWater
                && !player.isInLava
}
