package com.lambda.task.tasks

import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.threading.taskContext
import kotlinx.coroutines.Job
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BuildStructure(
    private val blueprint: Blueprint,
    private val collectDrops: Boolean = false,
    private val skipWeakBlocks: Boolean = false,
    private val pathing: Boolean = true,
    private val limitPerTick: Int = 20
) : Task<Unit>() {
    private var currentJob: Job? = null
    private var lastResult: BuildResult? = null

    init {
        listener<TickEvent.Pre> {
            val structure = when (blueprint) {
                is DynamicBlueprint -> blueprint.update(this)
                else -> blueprint.structure
            }

            val results = structure.entries.fold(emptySet<BuildResult>()) { acc, (pos, state) ->
                val buildContext = BreakContext(
                    pos,
                    Direction.entries.count { world.isAir(pos.offset(it)) },
                    player.pos.squaredDistanceTo(pos.toCenterPos())
                )

                acc + BreakResult.Success(buildContext)
            }

            results.sorted().take(limitPerTick).forEach { result ->
                if (lastResult == result) return@forEach
                if (result !is Resolvable) return@forEach

                lastResult = result
//                currentJob?.cancel()
                currentJob = taskContext {
                    result.resolve.steps.forEach { it.execute() }
                }
            }
        }
    }

    override suspend fun onAction() {
        awaitEvent<TickEvent.Pre> {
            blueprint.isDone(this) && false
        }
    }

    companion object {
        @TaskCha1nBuilder
        fun TaskChainBuilder.buildStructure(
            blueprint: Blueprint,
            collectDrops: Boolean = false,
            skipWeakBlocks: Boolean = false,
            pathing: Boolean = true,
        ) = BuildStructure(
                blueprint,
                collectDrops,
                skipWeakBlocks,
                pathing
            ).apply {
                required(this)
            }

        @TaskCha1nBuilder
        fun TaskChainBuilder.breakAndCollectBlock(
            blockPos: BlockPos
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            collectDrops = true
        ).apply {
            required(this)
        }
    }
}