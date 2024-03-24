package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.interaction.visibilty.VisibilityChecker.findRotation
import com.lambda.threading.runSafe
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.*

abstract class RotationEvent : Event {
    class Pre : RotationEvent(), ICancellable by Cancellable() {
        // Only one rotation can "win" each tick
        val requests = TreeSet<RotationRequest>(Comparator.reverseOrder())

        init {
            // Always check if baritone wants to rotate as well
            RotationManager.BaritoneProcessor.baritoneContext?.let { context ->
                requests.add(RotationRequest(context.config, context.rotation, -1))
            }
        }

        fun lookAtEntity(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            entity: LivingEntity,
            priority: Int = 0,
        ) {
            runSafe {
                findRotation(rotationConfig, interactionConfig, listOf(entity.boundingBox), priority) {
                    entityResult?.entity == entity
                }?.let {
                    requests.add(it)
                }
            }
        }

        fun lookAtBlock(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            blockPos: BlockPos,
            sides: Set<Direction> = emptySet(),
            priority: Int = 0,
        ) {
            runSafe {
                val state = world.getBlockState(blockPos)
                val voxelShape = state.getOutlineShape(world, blockPos)
                val boundingBoxes = voxelShape.boundingBoxes.map { it.offset(blockPos) }
                findRotation(rotationConfig, interactionConfig, boundingBoxes, priority, sides) {
                    blockResult?.blockPos == blockPos && (blockResult?.side in sides || sides.isEmpty())
                }?.let {
                    requests.add(it)
                }
            }
        }
    }

    class Post(val request: RotationRequest) : RotationEvent()
}