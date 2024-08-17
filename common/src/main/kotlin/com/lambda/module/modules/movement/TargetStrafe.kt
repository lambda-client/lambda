package com.lambda.module.modules.movement

import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import kotlin.math.pow

object TargetStrafe : Module(
    name = "TargetStrafe",
    description = "Automatically strafes around entities",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val targetDistance by setting("Strafe Distance", 1.0, 0.0..5.0, 0.1)
    private val jitterCompensation by setting("Jitter Compensation", 0.0, 0.0..1.0, 0.1)
    private val stabilize by setting("Stabilize", StabilizationMode.NORMAL)

    enum class StabilizationMode {
        NONE, WEAK, NORMAL, STRONG
    }

    private var forwardDirection = 1
    private var strafeDirection = 1

    @JvmStatic val isActive get() = isEnabled && KillAura.isEnabled && KillAura.target != null

    init {
        listener<TickEvent.Post> {
            if (player.horizontalCollision) strafeDirection *= -1

            if (KillAura.target == null) {
                forwardDirection = 1
                strafeDirection = 1
            }
        }

        listener<RotationEvent.StrafeInput> { event ->
            KillAura.target?.let { target ->
                event.strafeYaw = player.eyePos.rotationTo(target.boundingBox.center).yaw

                val distSq = player.pos distSq target.pos
                val keepRange = 0.5 * jitterCompensation

                forwardDirection = when {
                    distSq > (targetDistance + keepRange).pow(2) -> 1
                    distSq < (targetDistance - keepRange).pow(2) -> -1
                    else -> forwardDirection
                }

                // Premium code, do not touch it bites
                var shouldStabilize = when (stabilize) {
                    StabilizationMode.NONE -> false
                    StabilizationMode.WEAK -> player.age % 4 == 0   // 1/4
                    StabilizationMode.NORMAL -> player.age % 2 == 0 // 2/4
                    StabilizationMode.STRONG -> player.age % 4 != 0 // 3/4
                }

                shouldStabilize = shouldStabilize && distSq > (targetDistance + 0.5).pow(2)

                var strafe = strafeDirection.toDouble()
                if (shouldStabilize) strafe = 0.0

                event.input.mergeFrom(
                    buildMovementInput(
                        forwardDirection.toDouble(),
                        strafe,
                        true
                    )
                )
            }
        }

        onEnable {
            forwardDirection = 1
            strafeDirection = 1
        }
    }
}