package com.lambda.event.events

import com.lambda.config.RotationSettings
import com.lambda.event.Event
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable
import com.lambda.manager.RotationManager
import com.lambda.manager.interaction.InteractionConfig
import com.lambda.manager.rotation.IRotationConfig
import com.lambda.manager.rotation.Rotation
import com.lambda.manager.rotation.Rotation.Companion.distance
import com.lambda.manager.rotation.Rotation.Companion.rotationTo
import com.lambda.manager.interaction.VisibilityChecker.scanVisibleSurfaces
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import java.util.*

abstract class RotationEvent : Event {
    class Pre : RotationEvent(), ICancellable by Cancellable() {
        // Only one rotation can "win" each tick
        val requests = TreeSet<RotationRequest>(Comparator.reverseOrder())

        init {
            // Always check if baritone wants to rotate as well
            RotationManager.BaritoneProcessor.baritoneContext?.let { context ->
                requests.add(RotationRequest(-1, context.config, context.rotation))
            }
        }

        fun rotateTo(
            config: IRotationConfig,
            rotation: Rotation,
            priority: Int = 0,
        ) {
            requests.add(RotationRequest(priority, config, rotation))
        }

        fun lookAt(
            rotationConfig: IRotationConfig,
            interact: InteractionConfig,
            box: Box,
            priority: Int = 0,
            hitCheck: HitResult.() -> Boolean,
        ) {
            runSafe {
                if (box.contains(player.eyePos)) {
                    stay(priority, rotationConfig)
                    return@runSafe
                }

                val currentRotation = RotationManager.currentRotation
                val currentCast = currentRotation.rayCast(
                    interact.reach,
                    interact.rayCastMask,
                    player.eyePos
                )
                val check = currentCast?.let { it.hitCheck() } ?: false

//                 Slowdown or freeze if looking correct
                (rotationConfig as? RotationSettings)?.slowdownIf(check) ?: run {
                    if (check) stay(priority, rotationConfig)
                    return@runSafe
                }

                val reachSq = interact.reach * interact.reach

                var closestRotation: Rotation? = null
                var rotationDist = 0.0

                scanVisibleSurfaces(box, interact.resolution) { vec ->
                    if (player.eyePos distSq vec > reachSq) return@scanVisibleSurfaces

                    val newRotation = player.eyePos.rotationTo(vec)

                    val cast = newRotation.rayCast(
                        interact.reach,
                        interact.rayCastMask,
                        player.eyePos
                    ) ?: return@scanVisibleSurfaces
                    if (!cast.hitCheck()) return@scanVisibleSurfaces

                    val dist = newRotation.distance(currentRotation)
                    if (dist >= rotationDist && closestRotation != null) return@scanVisibleSurfaces

                    rotationDist = dist
                    closestRotation = newRotation
                }

                // Rotate to selected point
                closestRotation?.let { rotation ->
                    requests.add(RotationRequest(priority, rotationConfig, rotation))
                }
            }

        }

        fun lookAt(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            entity: LivingEntity,
            priority: Int = 0,
        ) {
            lookAt(rotationConfig, interactionConfig, entity.boundingBox, priority) {
                entityResult?.entity == entity
            }
        }

        private fun stay(priority: Int = 0, config: IRotationConfig) {
            requests.add(RotationRequest(priority, config, RotationManager.currentRotation))
        }

        data class RotationRequest(
            val priority: Int,
            val config: IRotationConfig,
            val rotation: Rotation,
        ) : Comparable<RotationRequest> {
            override fun compareTo(other: RotationRequest) =
                priority.compareTo(other.priority)
        }
    }
    class Post : RotationEvent()
}