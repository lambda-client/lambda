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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.request.rotation.RotationMode
import com.lambda.interaction.request.rotation.visibilty.lookAt
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
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    @JvmStatic
    val mode by setting("Mode", Mode.GRIM_STRAFE).onValueChange { _, _ -> reset() }

    // Grim
    private val diagonal by setting("Diagonal", true) { mode == Mode.GRIM_STRAFE }
    private val grimEntityBoost by setting("Entity Boost", 1.0, 0.0..2.0, 0.01) { mode == Mode.GRIM_STRAFE }
    private val grimCollideMultiplier by setting("Entity Collide Multiplier", 0.5, 0.0..1.0, 0.01)  { mode == Mode.GRIM_STRAFE && grimEntityBoost > 0.0 }
    private val grimBoatBoost by setting("Boat Boost", 0.4, 0.0..1.0, 0.01) { mode == Mode.GRIM_STRAFE }
    private val grimMaxSpeed by setting("Max Speed", 1.0, 0.2..1.0, 0.01) { mode == Mode.GRIM_STRAFE }

    // NCP
    private val strict by setting("Strict", true) { mode == Mode.NCP_STRAFE }
    private val lowerJump by setting("Lower Jump", true) { mode == Mode.NCP_STRAFE }
    private val ncpAutoJump by setting("Auto Jump", false) { mode == Mode.NCP_STRAFE }
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01) { mode == Mode.NCP_STRAFE }

    // Grim
    private val rotationConfig = RotationConfig.Instant(RotationMode.Sync, Int.MIN_VALUE + 1)

    private var prevTickJumping = false

    // NCP
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

            when (mode) {
                Mode.GRIM_STRAFE -> handleGrim()
                Mode.NCP_STRAFE -> handleStrafe()
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

        listen<MovementEvent.InputUpdate>(Int.MIN_VALUE) {
            if (mode != Mode.GRIM_STRAFE || !shouldWork()) return@listen

            // Delay jumping key state by 1 tick to let the rotation predict jump timing
            it.input.apply {
                val jump = jumping
                jumping = prevTickJumping
                prevTickJumping = jump
            }
        }

        onRotate {
            if (mode != Mode.GRIM_STRAFE) return@onRotate
            if (!shouldWork()) return@onRotate

            var yaw = player.yaw
            val input = newMovementInput()

            if (!input.isInputting) return@onRotate

            run {
                if (!diagonal) return@run
                if (player.isOnGround && input.playerInput.jump) return@run

                val forward = input.roundedForward.toFloat()
                var strafe = input.roundedStrafing.toFloat()

                if (strafe == 0f) strafe = -1f
                if (forward == 0f) strafe *= -1

                yaw -= 45 * strafe
            }

            val moveYaw = calcMoveYaw(yaw, input.roundedForward, input.roundedStrafing)

            lookAt(
                Rotation(moveYaw, 0.0)
            ).requestBy(rotationConfig)
        }

        onEnable {
            reset()
        }
    }

    private fun SafeContext.handleGrim() {
        if (!isInputting) return
        if (player.moveDelta > grimMaxSpeed) return

        var boostAmount = 0.0

        boostAmount += entitySearch<LivingEntity>(3.0) {
            player.boundingBox.expand(1.0) in it.boundingBox && it !is ArmorStandEntity
        }.sumOf { 0.08 * grimEntityBoost }

        if (grimBoatBoost > 0.0) {
            boostAmount += entitySearch<BoatEntity>(4.0) {
                player.boundingBox in it.boundingBox.expand(0.01)
            }.sumOf { grimBoatBoost }
        }

        addSpeed(boostAmount)
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
            Mode.GRIM_STRAFE -> {
                !player.input.handledByBaritone && !TargetStrafe.isActive
            }

            Mode.NCP_STRAFE -> {
                !player.isSneaking
            }
        }
    }

    private fun reset() {
        ncpPhase = NCPPhase.SLOWDOWN
        ncpSpeed = NCP_BASE_SPEED
    }
}
