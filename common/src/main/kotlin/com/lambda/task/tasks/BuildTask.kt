package com.lambda.task.tasks

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

class BuildTask @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val pathing: Boolean = TaskFlow.build.pathing,
    private val stayInRange: Boolean = true,
    private val forceSilkTouch: Boolean = false,
    val collectDrops: Boolean = TaskFlow.build.collectDrops,
    private val cancelOnUnsolvable: Boolean = false,
) : Task<Unit>() {
    private var previousResults = setOf<BuildResult>()
    private var lastResult: BuildResult? = null

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

            val instantResults = results.filterIsInstance<BreakResult.Break>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(TaskFlow.build.breaksPerTick)

            if (TaskFlow.build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    lastResult = it
                    it.start(this@BuildTask, pauseParent = false)
                }
                return@listener
            }

            when (result) {
                is BuildResult.Done, is BuildResult.Unbreakable -> {
                    if (!finishOnDone) return@listener
                    success(Unit)
                }
                is Navigable -> {
                    if (lastResult?.isCompleted == false) return@listener

                    if (pathing) BaritoneUtils.setGoalAndPath(result.goal)
                }
                else -> {
                    if (lastResult?.isCompleted == false) return@listener

                    LOG.info("Resolving: $result")

                    if (result is BreakResult.Break) {
                        result.collectDrop = collectDrops
                    }

                    lastResult = result
                    result.start(this@BuildTask, pauseParent = result.pausesParent)
                }
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun build(
            finishOnDone: Boolean = true,
            pathing: Boolean = TaskFlow.build.pathing,
            stayInRange: Boolean = true,
            forceSilkTouch: Boolean = false,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            cancelOnUnsolvable: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildTask(
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
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            forceSilkTouch = withSilkTouch,
            stayInRange = stayInRange,
            collectDrops = true
        )

        @Ta5kBuilder
        fun breakBlock(
            blockPos: BlockPos,
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint()
        )
    }
}