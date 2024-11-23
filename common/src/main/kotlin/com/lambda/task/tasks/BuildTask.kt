/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.extension.Structure
import com.lambda.util.extension.tickDelta
import net.minecraft.util.math.BlockPos

class BuildTask @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val pathing: Boolean = TaskFlow.build.pathing,
    private val stayInRange: Boolean = true,
    private val forceSilkTouch: Boolean = false,
    val collectDrops: Boolean = TaskFlow.build.collectDrops,
) : Task<Unit>() {
    private var previousResults = setOf<BuildResult>()
    private val placeTimeout = 15
    private val pending = mutableListOf<BuildResult>()

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
            pending.removeIf {
                if (it.age > placeTimeout) {
                    it.cancel()
                    true
                } else {
                    it.isCompleted
                }
            }

            (blueprint as? DynamicBlueprint)?.update(this)

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listener
            }

            val results = blueprint.simulate(player.getCameraPosVec(mc.tickDelta.toFloat()))
            previousResults = results

            val instantResults = results.filterIsInstance<BreakResult.Break>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(TaskFlow.build.breaksPerTick)

            if (TaskFlow.build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    pending.add(it)
                    it.start(this@BuildTask, pauseParent = false)
                }
                return@listener
            }

            if (pending.isNotEmpty()) {
                return@listener
            }

            val result = results.minOrNull() ?: return@listener
            when {
                !result.rank.solvable -> {
//                    info("Unsolvable: $result")
                    if (!blueprint.isDone(this)) return@listener
                    success(Unit)
                }

                result is BuildResult.NotVisible -> {
                    if (pathing) BaritoneUtils.setGoalAndPath(
                        BuildGoal(blueprint.simulation())
                    )
                }

                result is Navigable -> {
                    if (pathing) BaritoneUtils.setGoalAndPath(result.goal)
                }

                else -> {
                    LOG.info("Resolving: $result")

                    if (result is BreakResult.Break) {
                        result.collectDrop = collectDrops
                    }

                    pending.add(result)
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
            collectDrops
        )

        @Ta5kBuilder
        fun Structure.build(
            finishOnDone: Boolean = true,
            pathing: Boolean = TaskFlow.build.pathing,
            stayInRange: Boolean = true,
            forceSilkTouch: Boolean = false,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            cancelOnUnsolvable: Boolean = true,
        ) = BuildTask(
            toBlueprint(),
            finishOnDone,
            pathing,
            stayInRange,
            forceSilkTouch,
            collectDrops
        )

        @Ta5kBuilder
        fun Blueprint.build(
            finishOnDone: Boolean = true,
            pathing: Boolean = TaskFlow.build.pathing,
            stayInRange: Boolean = true,
            forceSilkTouch: Boolean = false,
            collectDrops: Boolean = TaskFlow.build.collectDrops,
            cancelOnUnsolvable: Boolean = true,
        ) = BuildTask(
            this,
            finishOnDone,
            pathing,
            stayInRange,
            forceSilkTouch,
            collectDrops
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
