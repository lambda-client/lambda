package com.lambda.task.tasks

import baritone.api.pathing.goals.GoalNear
import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.DirectionMask.mask
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
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import java.awt.Color

class BuildStructure @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val pathing: Boolean = TaskFlow.build.pathing,
    private val stayInRange: Boolean = true,
    private val forceSilkTouch: Boolean = false,
    val collectDrops: Boolean = TaskFlow.build.collectDrops,
    private val cancelOnUnsolvable: Boolean = true,
) : Task<Unit>() {

    abstract class PathingStrategy {

    }

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
            val resBlock = results.associateBy { it.blockPos }

            TaskFlow.esp.clear()

            var sides = DirectionMask.ALL

            resBlock.forEach { (pos, res) ->
                Direction.entries
                    .filter { pos.offset(it) in resBlock.keys }
                    .forEach { sides = sides.exclude(it.mask) }

                TaskFlow.esp.build(
                    Box(pos),
                    Color(0, 255, 0, 50),
                    Color(0, 255, 0, 50),
                    sides,
                    DirectionMask.OutlineMode.AND
                )
            }
            TaskFlow.esp.upload()

            val instantResults = results.filterIsInstance<BreakResult.Success>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(TaskFlow.build.breaksPerTick)

            if (TaskFlow.build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    it.resolve.start(this@BuildStructure, pauseParent = false)
                }
                return@listener
            }

            results.minOrNull()?.let { result ->
                when (result) {
                    is BuildResult.Done -> checkDone()
                    is Resolvable -> {
                        LOG.info("Resolving: $result")
                        result.resolve.start(this@BuildStructure)
                        if (pathing && stayInRange) {
                            BaritoneUtils.setGoalAndPath(GoalNear(result.blockPos, 2))
                        }
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

        listener<RenderEvent.World> {
            TaskFlow.esp.render()
        }
    }

    private fun SafeContext.checkDone() {
        if (!finishOnDone) return
        BaritoneUtils.cancel()
        success(Unit)
        return
    }

    companion object {
        @Ta5kBuilder
        fun buildStructure(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            pathing: Boolean = TaskFlow.build.pathing,
            stayInRange: Boolean = true,
            cancelOnUnsolvable: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                finishOnDone,
                pathing,
                stayInRange,
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