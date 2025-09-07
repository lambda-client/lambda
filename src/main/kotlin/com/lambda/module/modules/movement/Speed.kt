/*
 * Copyright 2025 Lambda
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

import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.RotationConfig
import com.lambda.interaction.request.rotating.RotationMode
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.extension.contains
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.MovementUtils.calcMoveYaw
import com.lambda.util.player.MovementUtils.handledByBaritone
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.setSpeed
import com.lambda.util.world.entitySearch
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.vehicle.BoatEntity

object Speed : Module(
    name = "Speed",
    description = "Accelerates your walking speed",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic
    val mode by setting("Mode", Mode.GRIM_STRAFE).onValueChange { _, _ -> reset() }

    // Grim
    private val diagonal by setting("Diagonal", true) { mode == Mode.GRIM_STRAFE }

    // NCP
    private val strict by setting("Strict", true) { mode == Mode.NCP_STRAFE }
    private val lowerJump by setting("Lower Jump", true) { mode == Mode.NCP_STRAFE }
    private val ncpAutoJump by setting("Auto Jump", false) { mode == Mode.NCP_STRAFE }
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01) { mode == Mode.NCP_STRAFE }

    private val rotationConfig = RotationConfig.Instant(RotationMode.Sync)

    // NCP state variables
    const val NCP_BASE_SPEED = 0.2873
    private const val NCP_AIR_DECAY = 0.9937
    private var ncpPhase = NCPPhase.SLOWDOWN
    private var ncpSpeed = NCP_BASE_SPEED
    private var lastDistance = 0.0

    enum class Mode(override val displayName: String) : NamedEnum {
        GRIM_STRAFE("Grim Strafe"),
        NCP_STRAFE("NCP Strafe"),
    }

    private enum class NCPPhase {
        JUMP,
        JUMP_POST,
        SLOWDOWN
    }

    init {
        listen<MovementEvent.Player.Pre> {
            if (!shouldWork()) {
                reset()
                return@listen
            }

            if (mode == Mode.NCP_STRAFE) {
                handleStrafe()
            }
        }

        listen<MovementEvent.Player.Post> {
            lastDistance = player.moveDelta
        }

        listen<ClientEvent.TimerUpdate> {
            if (mode != Mode.NCP_STRAFE) return@listen
            if (!shouldWork() || !isInputting) return@listen
            it.speed = ncpTimerBoost
        }

        listen<MovementEvent.Jump> {
            if (mode == Mode.NCP_STRAFE && shouldWork()) it.cancel()
        }

        listen<UpdateManagerEvent.Rotation> {
            if (mode != Mode.GRIM_STRAFE || !shouldWork()) return@listen

            val input = newMovementInput()
            if (!input.isInputting) return@listen

            val intendedMoveYaw = calcMoveYaw(player.yaw, input.roundedForward, input.roundedStrafing)
            val targetYaw = if (diagonal && !(input.playerInput.jump && player.isOnGround)) {
                intendedMoveYaw - 45.0f
            } else intendedMoveYaw

            lookAt(
                Rotation(targetYaw, player.pitch.toDouble())
            ).requestBy(rotationConfig)
        }

        onEnable {
            reset()
        }
    }

    private fun SafeContext.handleStrafe() {
        val shouldJump = player.input.playerInput.jump || (ncpAutoJump && isInputting)

        if (player.isOnGround && shouldJump) {
            ncpPhase = NCPPhase.JUMP
        }

        ncpPhase = when (ncpPhase) {
            NCPPhase.JUMP -> {
                if (player.isOnGround) {
                    player.motionY = if (lowerJump) 0.4 else 0.42
                    ncpSpeed = NCP_BASE_SPEED + 0.3
                    NCPPhase.JUMP_POST
                } else NCPPhase.SLOWDOWN
            }

            NCPPhase.JUMP_POST -> {
                ncpSpeed *= if (strict) 0.59 else 0.62
                NCPPhase.SLOWDOWN
            }

            NCPPhase.SLOWDOWN -> {
                ncpSpeed = lastDistance * NCP_AIR_DECAY
                NCPPhase.SLOWDOWN
            }
        }

        if (player.isOnGround && !shouldJump) {
            ncpSpeed = NCP_BASE_SPEED
        }

        ncpSpeed = ncpSpeed
            .coerceAtMost(1.0)
            .coerceAtLeast(NCP_BASE_SPEED)

        val moveSpeed = if (isInputting) ncpSpeed else {
            ncpSpeed = NCP_BASE_SPEED
            0.0
        }

        setSpeed(moveSpeed)
    }

    private fun SafeContext.shouldWork(): Boolean {
        if (player.abilities.flying || player.isElytraFlying || player.isTouchingWater || player.isInLava) return false

        return when (mode) {
            Mode.GRIM_STRAFE -> !player.input.handledByBaritone
            Mode.NCP_STRAFE -> !player.isSneaking
        }
    }

    private fun reset() {
        ncpPhase = NCPPhase.SLOWDOWN
        ncpSpeed = NCP_BASE_SPEED
    }
}