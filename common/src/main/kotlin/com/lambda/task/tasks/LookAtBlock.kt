package com.lambda.task.tasks

import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationContext
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class LookAtBlock(
    val blockPos: BlockPos,
    private val rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
    private val interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
    private val sides: Set<Direction> = emptySet()
) : Task<RotationContext>() {
    private var context: RotationContext? = null
    init {
        listener<RotationEvent.Pre> {
            context = it.lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
        }
    }

    override suspend fun onAction(): RotationContext {
        awaitEvent<RotationEvent.Post> { !it.context.isValid }

        return context ?: throw IllegalStateException("Failed to look at block")
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.lookAtBlock(
            blockPos: BlockPos,
            rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
            interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
            sides: Set<Direction> = emptySet()
        ) = LookAtBlock(blockPos, rotationConfig, interactionConfig, sides).apply {
            required(this)
        }
    }
}