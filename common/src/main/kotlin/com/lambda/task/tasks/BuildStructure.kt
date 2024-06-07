package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.*
import com.lambda.interaction.construction.simulation.BuildSimulator
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerManager.findBestAvailableTool
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.mostCenter
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.vecOf
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.OperatorBlock
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

class BuildStructure @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val pathing: Boolean = TaskFlow.build.pathing,
    val collectDrops: Boolean = TaskFlow.build.collectDrops,
    private val cancelOnUnsolvable: Boolean = true,
) : Task<Unit>() {
    private var lastTask: Task<*>? = null

    override fun SafeContext.onStart() {
        (blueprint as? DynamicBlueprint)?.create(this)
    }

    init {
        listener<TickEvent.Pre> {
            (blueprint as? DynamicBlueprint)?.update(this)

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listener
            }

            val results = blueprint.simulate(player.getCameraPosVec(mc.tickDelta))

            val instantResults = results.filterIsInstance<BreakResult.Success>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(TaskFlow.build.breaksPerTick)

            if (TaskFlow.build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    it.resolve.start(this@BuildStructure, pauseParent = false)
                }
                lastTask = instantResults.last().resolve
                return@listener
            }

            results.minOrNull()?.let { result ->
                if (!pathing && result is Navigable) return@let

                if (result !is Resolvable) {
                    if (result is BuildResult.Done) {
                        checkDone()
                    } else if (cancelOnUnsolvable) {
                        failure("Failed to resolve build result: $result")
                        return@listener
                    }
                    return@listener
                }
                lastTask = result.resolve

                LOG.info("Resolving: $result")
                result.resolve.start(this@BuildStructure)
            }
        }
    }

    private fun SafeContext.checkDone() {
        if (!finishOnDone) return

//        cancelSubTasks()
        success(Unit)
        return
    }

    companion object {
        @Ta5kBuilder
        fun buildStructure(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            pathing: Boolean = TaskFlow.build.pathing,
            cancelOnUnsolvable: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                finishOnDone,
                pathing,
                collectDrops,
                cancelOnUnsolvable
            )

        @Ta5kBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos,
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            collectDrops = true
        )

        @Ta5kBuilder
        fun breakBlock(
            blockPos: BlockPos,
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint()
        )
    }
}