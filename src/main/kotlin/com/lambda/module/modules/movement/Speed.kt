/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.movement

import com.lambda.config.applyEdits
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.extension.contains
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.MovementUtils.calcMoveYaw
import com.lambda.util.player.MovementUtils.handledByBaritone
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.setSpeed
import com.lambda.util.world.entitySearch
import net.minecraft.entity.vehicle.BoatEntity

object Speed : Module(
    name = "Speed",
    description = "Accelerates your walking speed",
    tag = ModuleTag.Movement,
) {
    @JvmStatic
    val mode by setting("Mode", Mode.GrimStrafe)
        .onValueChange { _, _ -> reset() }

    // Grim
    private val diagonal by setting("Diagonal", true) { mode == Mode.GrimStrafe }
    private val grimBoatBoost by setting("Boat Boost", 0.4, 0.0..1.7, 0.01) { mode == Mode.GrimStrafe }

    // NCP
    private val strict by setting("Strict", true) { mode == Mode.NcpStrafe }
    private val lowerJump by setting("Lower Jump", true) { mode == Mode.NcpStrafe }
    private val ncpAutoJump by setting("Auto Jump", false) { mode == Mode.NcpStrafe }
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01) { mode == Mode.NcpStrafe }

    // NCP state variables
    const val NcpBaseSpeed = 0.2873
    private const val NcpAirDecay = 0.9937
    private var ncpPhase = NCPPhase.SlowDown
    private var ncpSpeed = NcpBaseSpeed
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
        setDefaultAutomationConfig {
            applyEdits {
                hideAllBlocksExcept(rotationConfig)
            }
        }

        listen<MovementEvent.Player.Pre> {
            if (!shouldWork()) {
                reset()
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
            reset()
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
                    ncpSpeed = NcpBaseSpeed + 0.3
                    NCPPhase.JumpPost
                } else NCPPhase.SlowDown
            }

            NCPPhase.JumpPost -> {
                ncpSpeed *= if (strict) 0.59 else 0.62
                NCPPhase.SlowDown
            }

            NCPPhase.SlowDown -> {
                ncpSpeed = lastDistance * NcpAirDecay
                NCPPhase.SlowDown
            }
        }

        if (player.isOnGround && !shouldJump) {
            ncpSpeed = NcpBaseSpeed
        }

        ncpSpeed = ncpSpeed.coerceIn(NcpBaseSpeed..1.0)

        val moveSpeed = if (isInputting) ncpSpeed else {
            ncpSpeed = NcpBaseSpeed
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
            Mode.GrimStrafe -> !player.input.handledByBaritone
            Mode.NcpStrafe -> !player.isSneaking
        }
    }

    private fun reset() {
        ncpPhase = NCPPhase.SlowDown
        ncpSpeed = NcpBaseSpeed
    }
}
