package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BreakBlock(
    private val blockPos: BlockPos,
    private val rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
    private val interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
    private val sides: Set<Direction> = emptySet(),
    private val collectDrop: Boolean = false,
    private val noRotationForInstant: Boolean = true,
) : Task<Unit>() {
    val SafeContext.state: BlockState get() = world.getBlockState(blockPos)

    init {
//        listener<RotationEvent.Pre> { event ->
//            if (instantBreakable(state, blockPos) && noRotationForInstant) return@listener
//            event.lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
//        }
//
//        listener<RotationEvent.Post> {
//            if (instantBreakable(state, blockPos) && noRotationForInstant) return@listener
//            if (!it.context.isValid) return@listener
//            val hitResult = it.context.hitResult?.blockResult ?: return@listener
//
//            if (interaction.updateBlockBreakingProgress(hitResult.blockPos, hitResult.side)) {
//                mc.particleManager.addBlockBreakingParticles(hitResult.blockPos, hitResult.side)
//                player.swingHand(Hand.MAIN_HAND)
//            }
//        }

        listener<TickEvent.Pre> {
//            if (!instantBreakable(state, blockPos) || !noRotationForInstant) return@listener
            val shape = state.getCollisionShape(world, blockPos)

            if (shape.isEmpty) {
                LOG.info("BreakBlock $blockPos has $state and empty shape")
                return@listener
            }

            state.getCollisionShape(world, blockPos).boundingBox
                .getVisibleSurfaces(player.eyePos).firstOrNull()?.let { side ->
                    interaction.attackBlock(blockPos, side)
//                    if (interaction.updateBlockBreakingProgress(blockPos, side)) {
//                        mc.particleManager.addBlockBreakingParticles(blockPos, side)
////                mc.particleManager.addParticle(ParticleTypes.CRIT, hitResult.pos.x, hitResult.pos.y, hitResult.pos.z, 0.0, 0.0, 0.0)
//                        player.swingHand(Hand.MAIN_HAND)
//                    }
                }
        }
    }

    override suspend fun onAction() {
        awaitEvent<TickEvent.Post> { world.isAir(blockPos) }

//        if (collectDrop) awaitEvent<WorldEvent.EntitySpawn> { it.entity is ItemEntity ... }
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.breakBlock(
            blockPos: BlockPos,
            rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
            interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
            sides: Set<Direction> = emptySet(),
            collectDrop: Boolean = false,
            noRotationForInstant: Boolean = true,
        ) = BreakBlock(
            blockPos,
            rotationConfig,
            interactionConfig,
            sides,
            collectDrop,
            noRotationForInstant
        ).apply {
            required(this)
        }
    }
}