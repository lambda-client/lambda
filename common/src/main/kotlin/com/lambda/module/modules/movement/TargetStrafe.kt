package com.lambda.module.modules.movement

import com.lambda.config.groups.IRotationConfig
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import com.lambda.util.world.WorldUtils.getClosestEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import kotlin.math.pow

object TargetStrafe : Module(
    name = "TargetStrafe",
    description = "Automatically strafes around entities",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val range by setting("Range", 6.0, 2.0..10.0, 0.1)
    private val targetDistance by setting("Target Distance", 1.0, 0.0..5.0, 0.1)
    private val jitterCompensation by setting("Jitter Compensation", 0.0, 0.0..1.0, 0.1)
    private val stabilize by setting("Stabilize", StabilizationMode.NORMAL)

    enum class StabilizationMode {
        NONE, WEAK, NORMAL, STRONG
    }

    private var targetEntity: LivingEntity? = null

    private var forwardDirection = 1
    private var strafeDirection = 1

    private val rotationConfig = object : IRotationConfig.Instant {
        override val rotationMode = RotationMode.SYNC
    }

    @JvmStatic val isActive get() = isEnabled && targetEntity != null

    init {
        listener<TickEvent.Post> {
            // ToDo: Use KillAura.target instead
            targetEntity = getClosestEntity<LivingEntity>(
                player.pos, range,
                predicate = { e -> e !is ArmorStandEntity }
            )

            if (player.horizontalCollision) strafeDirection *= -1

            if (targetEntity == null) {
                forwardDirection = 1
                strafeDirection = 1
            }
        }

        listener<RotationEvent.Strafe> { event ->
            targetEntity?.let {
                event.strafeYaw = player.eyePos.rotationTo(it.boundingBox.center).yaw
            }
        }

        listener<RotationEvent.Update> { event ->
            targetEntity?.let {
                event.context = RotationContext(
                    player.eyePos.rotationTo(it.boundingBox.center),
                    rotationConfig
                )
            }
        }

        listener<MovementEvent.InputUpdate> { event ->
            targetEntity?.let { target ->
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
            targetEntity = null
            forwardDirection = 1
            strafeDirection = 1
        }
    }
}