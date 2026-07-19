
package com.minato.module.modules.movement

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.ClientEvent
import com.minato.event.events.MovementEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.NamedEnum
import com.minato.util.extension.contains
import com.minato.util.extension.isElytraFlying
import com.minato.util.player.MovementUtils.addSpeed
import com.minato.util.player.MovementUtils.calcMoveYaw
import com.minato.util.player.MovementUtils.isInputting
import com.minato.util.player.MovementUtils.motionY
import com.minato.util.player.MovementUtils.moveDelta
import com.minato.util.player.MovementUtils.newMovementInput
import com.minato.util.player.MovementUtils.roundedForward
import com.minato.util.player.MovementUtils.roundedStrafing
import com.minato.util.player.MovementUtils.setSpeed
import com.minato.util.world.entitySearch
import net.minecraft.entity.vehicle.BoatEntity

object Speed : Module(
    name = "Speed",
    description = "Accelerates your walking speed",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic
    val mode by setting("Mode", Mode.GrimStrafe)
        .onValueChange { _, _ -> resetNcp() }

    // Grim
    private val diagonal by setting("Diagonal", true) { mode == Mode.GrimStrafe }
    private val grimBoatBoost by setting("Boat Boost", 0.4, 0.0..1.7, 0.01) { mode == Mode.GrimStrafe }

    // NCP
    private val strict by setting("Strict", true) { mode == Mode.NcpStrafe }
    private val lowerJump by setting("Lower Jump", true) { mode == Mode.NcpStrafe }
    private val ncpAutoJump by setting("Auto Jump", false) { mode == Mode.NcpStrafe }
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01) { mode == Mode.NcpStrafe }

    // NCP state variables
    const val NCP_BASE_SPEED = 0.2873
    private const val NCP_AIR_DECAY = 0.9937
    private var ncpPhase = NCPPhase.SlowDown
    private var ncpSpeed = NCP_BASE_SPEED
    private var lastDistance = 0.0

    enum class Mode(override val displayName: String) : NamedEnum {
        GrimStrafe("Grim Strafe"),
        NcpStrafe("NCP Strafe"),
    }

    private enum class NCPPhase {
        Jump,
        JumpPost,
        SlowDown
    }

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::rotationConfig)
            }

        listen<MovementEvent.Player.Pre> {
            if (!shouldWork()) {
                resetNcp()
                return@listen
            }

            when (mode) {
                Mode.NcpStrafe -> handleStrafe()
                Mode.GrimStrafe -> handleGrim()
            }
        }

        listen<MovementEvent.Player.Post> {
            lastDistance = player.moveDelta
        }

        listen<ClientEvent.TimerUpdate> {
            if (mode != Mode.NcpStrafe) return@listen
            if (!shouldWork() || !isInputting) return@listen
            it.speed = ncpTimerBoost
        }

        listen<MovementEvent.Jump> {
            if (mode == Mode.NcpStrafe && shouldWork()) it.cancel()
        }

        listen<TickEvent.Pre> {
            if (mode != Mode.GrimStrafe || !shouldWork()) return@listen

            val input = newMovementInput()
            if (!input.isInputting) return@listen

            val intendedMoveYaw = calcMoveYaw(player.yaw, input.roundedForward, input.roundedStrafing)
            val targetYaw = if (diagonal && !(input.playerInput.jump && player.isOnGround)) {
                intendedMoveYaw - 45.0f
            } else intendedMoveYaw

            rotationRequest { rotation(targetYaw, player.pitch.toDouble()) }.submit()
        }

        onEnable {
            resetNcp()
        }
    }

    private fun SafeContext.handleGrim() {
        var grimSpeed = 0.0

        if (grimBoatBoost > 0.0) {
            grimSpeed += entitySearch<BoatEntity>(4.0) {
                player.boundingBox in it.boundingBox.expand(0.01)
            }.sumOf { grimBoatBoost }
        }

        addSpeed(
            if (isInputting) grimSpeed else 0.0
        )
    }

    private fun SafeContext.handleStrafe() {
        val shouldJump = player.input.playerInput.jump || (ncpAutoJump && isInputting)

        if (player.isOnGround && shouldJump) {
            ncpPhase = NCPPhase.Jump
        }

        ncpPhase = when (ncpPhase) {
            NCPPhase.Jump -> {
                if (player.isOnGround) {
                    player.motionY = if (lowerJump) 0.4 else 0.42
                    ncpSpeed = NCP_BASE_SPEED + 0.3
                    NCPPhase.JumpPost
                } else NCPPhase.SlowDown
            }

            NCPPhase.JumpPost -> {
                ncpSpeed *= if (strict) 0.59 else 0.62
                NCPPhase.SlowDown
            }

            NCPPhase.SlowDown -> {
                ncpSpeed = lastDistance * NCP_AIR_DECAY
                NCPPhase.SlowDown
            }
        }

        if (player.isOnGround && !shouldJump) {
            ncpSpeed = NCP_BASE_SPEED
        }

        ncpSpeed = ncpSpeed.coerceIn(NCP_BASE_SPEED..1.0)

        val moveSpeed = if (isInputting) ncpSpeed else {
            ncpSpeed = NCP_BASE_SPEED
            0.0
        }

        setSpeed(moveSpeed)
    }

    private fun SafeContext.shouldWork(): Boolean {
        if (player.abilities.flying
            || player.isElytraFlying
            || player.isTouchingWater
            || player.isInLava
            || player.hasVehicle()) return false

        return when (mode) {
            Mode.GrimStrafe -> true
            Mode.NcpStrafe -> !player.isSneaking
        }
    }

    private fun resetNcp() {
        ncpPhase = NCPPhase.SlowDown
        ncpSpeed = NCP_BASE_SPEED
    }
}
