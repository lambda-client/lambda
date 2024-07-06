package com.lambda.task.tasks

import baritone.api.pathing.goals.GoalNear
import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.result.*
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import net.minecraft.util.math.BlockPos

class BuildStructure @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val pathing: Boolean = TaskFlow.build.pathing,
    private val stayInRange: Boolean = true,
    private val forceSilkTouch: Boolean = false,
    val collectDrops: Boolean = TaskFlow.build.collectDrops,
    private val cancelOnUnsolvable: Boolean = true,
) : Task<Unit>() {
    private var previousResults = setOf<BuildResult>()
    private var lastResult: BuildResult? = null
    private var lastTask: Task<*>? = null

    override fun SafeContext.onStart() {
        (blueprint as? DynamicBlueprint)?.create(this)
    }

    init {
        listener<RenderEvent.StaticESP> {
            previousResults.filterIsInstance<Drawable>().forEach { res ->
                with(res) { buildRenderer() }
            }
        }

        listener<TickEvent.Pre> {
            (blueprint as? DynamicBlueprint)?.update(this)

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listener
            }

            val results = blueprint.simulate(player.getCameraPosVec(mc.tickDelta))
            previousResults = results
            val result = results.minOrNull() ?: return@listener

            lastResult?.let {
                if (lastTask?.isCompleted == false && result.rank == it.rank) return@listener
//                if (lastTask?.isCompleted == true || it.rank.compareTo(result.rank) == 0) return@listener
//                if (it.pausesParent && lastTask?.isCompleted != true) return@listener
//                if (/*collectDrops && it is BreakResult.Success && */lastTask?.isCompleted == false && lastTask?.isFailed == false) {
//                    return@listener
//                }
                LOG.info("${it.rank.name}${if (it.pausesParent) " (paused)" else ""} -> ${result.rank.name} (${lastTask?.identifier})")

                lastTask?.cancel()
            }

            val instantResults = results.filterIsInstance<BreakResult.Success>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(TaskFlow.build.breaksPerTick)

            if (TaskFlow.build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    lastResult = it
                    lastTask = it.resolve.start(this@BuildStructure, pauseParent = false)
                }
                return@listener
            }

            when (result) {
                is BuildResult.Done, is BuildResult.Unbreakable -> {
                    if (!finishOnDone) return@listener
                    success(Unit)
                }
                is Resolvable -> {
                    LOG.info("Resolving: $result")

                    if (result is BreakResult.Success) {
                        result.collectDrop = collectDrops
                    }

                    lastResult = result
                    lastTask = result.resolve
                        .start(this@BuildStructure, pauseParent = result.pausesParent)
//                    if (pathing) {
//                        BaritoneUtils.setGoalAndPath(GoalNear(result.blockPos, 3))
//                    }
                }
                is Navigable -> {
                    if (pathing) BaritoneUtils.setGoalAndPath(result.goal)
                }
                else -> {
                    if (!cancelOnUnsolvable) return@listener

                    failure("Failed to resolve build result: $result")
                }
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun buildStructure(
            finishOnDone: Boolean = true,
            pathing: Boolean = TaskFlow.build.pathing,
            stayInRange: Boolean = true,
            forceSilkTouch: Boolean = false,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            cancelOnUnsolvable: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                finishOnDone,
                pathing,
                stayInRange,
                forceSilkTouch,
                collectDrops,
                cancelOnUnsolvable
            )

        @Ta5kBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos,
            withSilkTouch: Boolean = false,
            stayInRange: Boolean = false,
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            forceSilkTouch = withSilkTouch,
            stayInRange = stayInRange,
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