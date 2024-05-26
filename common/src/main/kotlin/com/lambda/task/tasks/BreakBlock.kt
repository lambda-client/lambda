package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Ta5kBuilder
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BreakBlock @Ta5kBuilder constructor(
    private val blockPos: BlockPos,
    private val rotationConfig: IRotationConfig = TaskFlow.rotationSettings,
    private val interactionConfig: InteractionConfig = TaskFlow.interactionSettings,
    private val sides: Set<Direction> = emptySet(),
    private val collectDrop: Boolean = false,
    private val noRotationForInstant: Boolean = true,
) : Task<ItemEntity?>() {
    private var beginState: BlockState? = null
    val SafeContext.state: BlockState get() = blockPos.blockState(world)

    override fun SafeContext.onStart() {
        if (state.isAir && !collectDrop) {
            success(null)
            return
        }
        beginState = state
    }

    init {
        listener<RotationEvent.Pre> { event ->
            if (instantBreakable(state, blockPos) && noRotationForInstant) return@listener
            event.lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
        }

        listener<RotationEvent.Post> {
            if (instantBreakable(state, blockPos) && noRotationForInstant) return@listener
            if (!it.context.isValid) return@listener
            val hitResult = it.context.hitResult?.blockResult ?: return@listener

            breakBlock(hitResult.side)
        }

        listener<TickEvent.Pre> {
            if (state.isAir && !collectDrop) {
                success(null)
                return@listener
            }

            if (!instantBreakable(state, blockPos) || !noRotationForInstant) return@listener
            val shape = state.getOutlineShape(world, blockPos)

            if (shape.isEmpty) {
                failure("${blockPos.toShortString()} in state $state has no outline shape")
                return@listener
            }

            shape.boundingBox
                .getVisibleSurfaces(player.eyePos).firstOrNull()?.let { side ->
                    breakBlock(side)
                }
        }

        listener<TickEvent.Post> {
            if (state.isAir && !collectDrop) success(null)
        }

        listener<WorldEvent.EntitySpawn> {
            if (it.entity is ItemEntity
                && it.entity.pos.isInRange(blockPos.toCenterPos(), 1.0)
//                && it.entity.stack.item == beginState?.block?.item // ToDo: The item entities are all air??
            ) success(it.entity)
        }
    }

    private fun SafeContext.breakBlock(side: Direction) {
        if (interaction.updateBlockBreakingProgress(blockPos, side)) {
            mc.particleManager.addBlockBreakingParticles(blockPos, side)
//            mc.particleManager.addParticle(ParticleTypes.CRIT, hitResult.pos.x, hitResult.pos.y, hitResult.pos.z, 0.0, 0.0, 0.0)
            if (!instantBreakable(state, blockPos) || !noRotationForInstant) player.swingHand(Hand.MAIN_HAND)
        }
    }

    companion object {
        @TaskCha1nBuilder
        fun uncheckedBreak(
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
        )
    }
}