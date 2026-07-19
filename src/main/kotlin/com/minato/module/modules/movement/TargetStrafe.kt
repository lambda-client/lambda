
package com.minato.module.modules.movement

import com.minato.event.events.RotationEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.minato.module.Module
import com.minato.module.modules.combat.KillAura
import com.minato.module.tag.ModuleTag
import com.minato.util.math.distSq
import com.minato.util.player.MovementUtils.update
import kotlin.math.pow

object TargetStrafe : Module(
    name = "TargetStrafe",
    description = "Automatically strafes around entities",
    tag = ModuleTag.MOVEMENT,
) {
    private val targetDistance by setting("Strafe Distance", 1.0, 0.0..5.0, 0.1)
    private val jitterCompensation by setting("Jitter Compensation", 0.0, 0.0..1.0, 0.1)
    private val stabilize by setting("Stabilize", StabilizationMode.Normal)

    enum class StabilizationMode {
        None,
        Weak,
        Normal,
        Strong
    }

    private var forwardDirection = 1.0
    private var strafeDirection = 1.0

    @JvmStatic
    val isActive get() = isEnabled && KillAura.isEnabled && KillAura.target != null

    init {
        listen<TickEvent.Post> {
            if (player.horizontalCollision) strafeDirection *= -1

            if (KillAura.target == null) {
                forwardDirection = 1.0
                strafeDirection = 1.0
            }
        }

        listen<RotationEvent.StrafeInput> { event ->
            KillAura.target?.let { target ->
                event.strafeYaw = player.eyePos.rotationTo(target.boundingBox.center).yaw

                val distSq = player.pos distSq target.pos
                val keepRange = 0.5 * jitterCompensation

                forwardDirection = when {
                    distSq > (targetDistance + keepRange).pow(2) -> 1.0
                    distSq < (targetDistance - keepRange).pow(2) -> -1.0
                    else -> forwardDirection
                }

                // Premium code, do not touch it bites
                var shouldStabilize = when (stabilize) {
                    StabilizationMode.None -> false
                    StabilizationMode.Weak -> player.age % 4 == 0   // 1/4
                    StabilizationMode.Normal -> player.age % 2 == 0 // 2/4
                    StabilizationMode.Strong -> player.age % 4 != 0 // 3/4
                }

                shouldStabilize = shouldStabilize && distSq > (targetDistance + 0.5).pow(2)

                val strafe = if (shouldStabilize) 0.0 else strafeDirection
                event.input.update(forwardDirection, strafe, jump = true)
            }
        }

        onEnable {
            forwardDirection = 1.0
            strafeDirection = 1.0
        }
    }
}
