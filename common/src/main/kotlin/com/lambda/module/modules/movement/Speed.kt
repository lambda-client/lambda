package com.lambda.module.modules.movement

import com.lambda.config.groups.IRotationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager.requestRotation
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Nameable
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.calcMoveYaw
import com.lambda.util.player.MovementUtils.handledByBaritone
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.mergeFrom
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.setSpeed
import com.lambda.util.primitives.extension.contains
import com.lambda.util.world.WorldUtils.getFastEntities
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.vehicle.BoatEntity

object Speed : Module(
    name = "Speed",
    description = "Accelerates your walking speed",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    @JvmStatic val mode by setting("Mode", Mode.GRIM_STRAFE).apply {
        onValueChange { _, _ ->
            reset()
        }
    }

    // Grim
    private val grimEntityBoost by setting("Entity Boost", 1.0, 0.0..2.0, 0.01) { mode == Mode.GRIM_STRAFE }
    private val grimCollideMultiplier by setting("Entity Collide Multiplier", 0.5, 0.0..1.0, 0.01)  { mode == Mode.GRIM_STRAFE && grimEntityBoost > 0.0}
    private val grimBoatBoost by setting("Boat Boost", 0.4, 0.0..1.0, 0.01) { mode == Mode.GRIM_STRAFE }
    private val grimMaxSpeed by setting("Max Speed", 1.0, 0.2..1.0, 0.01)  { mode == Mode.GRIM_STRAFE }

    // NCP
    private val strict by setting("Strict", true) { mode == Mode.NCP_STRAFE }
    private val lowerJump by setting("Lower Jump", true) { mode == Mode.NCP_STRAFE }
    private val ncpAutoJump by setting("Auto Jump", false) { mode == Mode.NCP_STRAFE }
    private val ncpTimerBoost by setting("Timer Boost", 1.08, 1.0..1.1, 0.01) { mode == Mode.NCP_STRAFE }

    // Grim
    private val rotationConfig = object : IRotationConfig.Instant {
        override val rotationMode = RotationMode.SYNC
    }

    // NCP
    const val NCP_BASE_SPEED = 0.2873
    private const val NCP_AIR_DECAY = 0.9937

    private var ncpPhase = NCPPhase.SLOWDOWN
    private var ncpSpeed = NCP_BASE_SPEED
    private var lastDistance = 0.0

    enum class Mode(override val displayName: String) : Nameable.NamedEnum {
        GRIM_STRAFE("Grim Strafe"),
        NCP_STRAFE("NCP Strafe"),
    }

    private enum class NCPPhase {
        JUMP,
        JUMP_POST,
        SLOWDOWN
    }

    init {
        listener<MovementEvent.Pre> {
            if (!shouldWork()) {
                reset()
                return@listener
            }

            when (mode) {
                Mode.GRIM_STRAFE -> handleGrim()
                Mode.NCP_STRAFE -> handleStrafe()
            }
        }

        listener<MovementEvent.Post> {
            lastDistance = player.moveDelta
        }

        listener<ClientEvent.Timer> {
            if (mode != Mode.NCP_STRAFE) return@listener
            if (!shouldWork() || !isInputting) return@listener
            it.speed = ncpTimerBoost
        }

        listener<MovementEvent.Jump> {
            if (!shouldWork()) return@listener

            when (mode) {
                Mode.NCP_STRAFE -> it.cancel()
                else -> {}
            }
        }

        // TODO: Diagonal movement when not jumping
        // needs movement prediction engine or a workaround to detect jumping 1 tick before
        requestRotation(100, false,
            onUpdate = { lastContext ->
                if (mode != Mode.GRIM_STRAFE) return@requestRotation null
                if (!shouldWork() || !isInputting) return@requestRotation null
                if (player.input.handledByBaritone || TargetStrafe.isActive) return@requestRotation null

                val input = newMovementInput()
                val yaw = calcMoveYaw(player.yaw, input.roundedForward, input.roundedStrafing)
                val rotation = Rotation(yaw, lastContext?.rotation?.pitch ?: player.pitch.toDouble())

                RotationContext(rotation, rotationConfig)
            }, {}
        )

        onEnable {
            reset()
        }
    }

    private fun SafeContext.handleGrim() {
        if (!isInputting) return
        if (player.moveDelta > grimMaxSpeed) return

        var boostAmount = 0.0

        getFastEntities<LivingEntity>(
            player.pos, 3.0,
            predicate = { player.boundingBox.expand(1.0) in it.boundingBox && it !is ArmorStandEntity },
            iterator = { e, _ ->
                val colliding = player.boundingBox in e.boundingBox
                val multiplier = if (colliding) grimCollideMultiplier else 1.0
                boostAmount += 0.08 * grimEntityBoost * multiplier
            }
        )

        if (grimBoatBoost > 0.0) {
            getFastEntities<BoatEntity>(
                player.pos, 4.0,
                predicate = { player.boundingBox in it.boundingBox.expand(0.01) },
                iterator = { _, _ -> boostAmount += grimBoatBoost }
            )
        }

        addSpeed(boostAmount)
    }

    private fun SafeContext.handleStrafe() {
        val shouldJump = player.input.jumping || (ncpAutoJump && isInputting)

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

    private fun SafeContext.shouldWork() =
        !player.abilities.flying
                && !player.isFallFlying
                && !player.input.sneaking
                && !player.isTouchingWater
                && !player.isInLava

    private fun reset() {
        ncpPhase = NCPPhase.SLOWDOWN
        ncpSpeed = NCP_BASE_SPEED
    }
}
