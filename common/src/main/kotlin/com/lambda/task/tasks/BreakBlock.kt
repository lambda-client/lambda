package com.lambda.task.tasks

import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.RotationMode
import com.lambda.interaction.visibilty.VisibilityChecker
import com.lambda.interaction.visibilty.VisibilityChecker.bounds
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.util.primitives.extension.component6
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.client.particle.DamageParticle
import net.minecraft.client.particle.Particle
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

class BreakBlock(
    private val blockPos: BlockPos,
    private val rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
    private val interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
    private val sides: Set<Direction> = emptySet(),
    private val collectDrop: Boolean = false,
) : Task<Unit>() {

    init {
        listener<RotationEvent.Pre> { event ->
            event.lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
        }

        listener<RotationEvent.Post> {
            if (!it.context.isValid) return@listener
            val hitResult = it.context.hitResult?.blockResult ?: return@listener

            if (interaction.updateBlockBreakingProgress(hitResult.blockPos, hitResult.side)) {
                mc.particleManager.addBlockBreakingParticles(hitResult.blockPos, hitResult.side)
                player.swingHand(Hand.MAIN_HAND)
            }
        }
//        listener<TickEvent.Pre> {
//            world.getBlockState(blockPos).getCollisionShape(world, blockPos).boundingBoxes.firstOrNull()?.let { box ->
//                getVisibleSurfaces(box).firstOrNull()?.let { side ->
//                    if (interaction.updateBlockBreakingProgress(blockPos, side)) {
//                        mc.particleManager.addBlockBreakingParticles(blockPos, side)
////                mc.particleManager.addParticle(ParticleTypes.CRIT, hitResult.pos.x, hitResult.pos.y, hitResult.pos.z, 0.0, 0.0, 0.0)
//                        player.swingHand(Hand.MAIN_HAND)
//                    }
//                }
//            }
//        }
    }

    override suspend fun onAction() {
        awaitEvent<TickEvent.Post> { world.isAir(blockPos) }
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.breakBlock(
            blockPos: BlockPos,
            rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
            interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
            sides: Set<Direction> = emptySet(),
            collectDrop: Boolean = false,
        ) = BreakBlock(blockPos, rotationConfig, interactionConfig, sides, collectDrop).apply {
            required(this)
        }
    }
}