package com.lambda.event.events

import com.lambda.config.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable
import com.lambda.manager.RotationManager
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.distance
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.RotationTest
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
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

        private fun SafeContext.lookAt(
            rotationConfig: IRotationConfig,
            interact: InteractionConfig,
            boxes: List<Box>,
            priority: Int = 0,
            sides: Set<Direction> = emptySet(),
            hitCheck: HitResult.() -> Boolean,
        ) {
            val eye = player.getCameraPosVec(mc.tickDelta)

            if (boxes.any { it.contains(eye) }) {
                stay(priority, rotationConfig)
                return
            }

            val currentRotation = RotationManager.currentRotation
            val currentCast = currentRotation.rayCast(
                interact.reach,
                interact.rayCastMask,
                eye
            )
            val check = currentCast?.let { it.hitCheck() } ?: false

            // Slowdown or freeze if looking correct
            (rotationConfig as? RotationSettings)?.slowdownIf(check) ?: run {
                if (check) stay(priority, rotationConfig)
                return
            }

            val reachSq = interact.reach * interact.reach

            var closestRotation: Rotation? = null
            var rotationDist = 0.0

            boxes.forEach { box ->
                scanVisibleSurfaces(box, sides, interact.resolution) { vec ->
                    if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                    val newRotation = eye.rotationTo(vec)

                    val cast = newRotation.rayCast(
                        interact.reach,
                        interact.rayCastMask,
                        eye
                    ) ?: return@scanVisibleSurfaces
                    if (!cast.hitCheck()) return@scanVisibleSurfaces

                    val dist = newRotation.distance(currentRotation)
                    if (dist >= rotationDist && closestRotation != null) return@scanVisibleSurfaces

                    rotationDist = dist
                    closestRotation = newRotation
                }
            }

            // Rotate to selected point
            closestRotation?.let { rotation ->
                requests.add(RotationRequest(priority, rotationConfig, rotation))
            }
        }

        fun lookAt(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            entity: LivingEntity,
            priority: Int = 0,
        ) {
            runSafe {
                lookAt(rotationConfig, interactionConfig, listOf(entity.boundingBox), priority) {
                    entityResult?.entity == entity
                }
            }
        }

        fun lookAt(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            blockPos: BlockPos,
            sides: Set<Direction>,
            priority: Int = 0,
        ) {
            runSafe {
                val state = world.getBlockState(blockPos)
                val voxelShape = state.getOutlineShape(world, blockPos)
                val boundingBoxes = voxelShape.boundingBoxes.map { it.offset(blockPos) }
                lookAt(rotationConfig, interactionConfig, boundingBoxes, priority, sides) {
                    blockResult?.blockPos == blockPos && blockResult?.side in sides
                }
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