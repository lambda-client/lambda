package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.findRotation
import com.lambda.threading.runSafe
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

abstract class RotationEvent : Event {
    /**
     * This event allows listeners to register a rotation request to be executed that tick.
     *
     * CAUTION: The listener with the lowest priority will win as it is the last to override the context.
     *
     * @property context The rotation context that listeners can set. Only one rotation can "win" each tick.
     */
    class Pre : RotationEvent(), ICancellable by Cancellable() {
        // Only one rotation can "win" each tick
        var context: RotationContext? = null

        init {
            // Always check if baritone wants to rotate as well
            RotationManager.BaritoneProcessor.baritoneContext?.let { context ->
                this.context = RotationContext(context.rotation, context.config)
            }
        }

        fun lookAtEntity(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            entity: Entity,
        ) {
            runSafe {
                findRotation(rotationConfig, interactionConfig, listOf(entity.boundingBox)) {
                    entityResult?.entity == entity
                }?.let { rotationContext ->
                    context = rotationContext
                }
            }
        }

        fun lookAtBlock(
            rotationConfig: IRotationConfig,
            interactionConfig: InteractionConfig,
            blockPos: BlockPos,
            sides: Set<Direction> = emptySet(),
        ) {
            runSafe {
                val state = world.getBlockState(blockPos)
                val voxelShape = state.getOutlineShape(world, blockPos)
                val boundingBoxes = voxelShape.boundingBoxes.map { it.offset(blockPos) }
                findRotation(rotationConfig, interactionConfig, boundingBoxes, sides) {
                    blockResult?.blockPos == blockPos && (blockResult?.side in sides || sides.isEmpty())
                }?.let { rotationContext ->
                    context = rotationContext
                }
            }
        }
    }

    class Post(val context: RotationContext) : RotationEvent()
}
