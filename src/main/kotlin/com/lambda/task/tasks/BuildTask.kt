/*
 * Copyright 2025 Lambda
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

import baritone.api.pathing.goals.GoalBlock
import com.lambda.Lambda.LOG
import com.lambda.config.AutomationConfig.Companion.DEFAULT
import com.lambda.config.groups.EatConfig.Companion.reasonEating
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Contextual
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.construction.simulation.result.Navigable
import com.lambda.interaction.construction.simulation.result.Resolvable
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.simulation.result.results.GenericResult
import com.lambda.interaction.construction.simulation.result.results.InteractResult
import com.lambda.interaction.construction.simulation.result.results.PreSimResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.breaking.BreakRequest.Companion.breakRequest
import com.lambda.interaction.managers.interacting.InteractRequest.Companion.interactRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.tasks.EatTask.Companion.eat
import com.lambda.threading.runSafeAutomated
import com.lambda.util.Formatting.format
import com.lambda.util.extension.Structure
import com.lambda.util.extension.playerSlots
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

class BuildTask private constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean,
    private val collectDrops: Boolean,
    private val lifeMaintenance: Boolean,
    automated: Automated
) : Task<Structure>(), Automated by automated {
    override val name: String get() = "Building $blueprint with ${(breaks / (age / 20.0 + 0.001)).format(precision = 1)} b/s ${(placements / (age / 20.0 + 0.001)).format(precision = 1)} p/s"

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()
    private val atMaxPendingInteractions
        get() = pendingInteractions.size >= buildConfig.maxPendingActions

    private var placements = 0
    private var breaks = 0
    private val dropsToCollect = mutableSetOf<ItemEntity>()
    var eatTask: EatTask? = null

    private val onItemDrop: ((item: ItemEntity) -> Unit)?
        get() = if (collectDrops) { item ->
            dropsToCollect.add(item)
        } else null

    override fun SafeContext.onStart() {
        iteratePropagating()
    }

    init {
        listen<TickEvent.Pre> {
            when {
                lifeMaintenance && eatTask == null && runSafeAutomated { reasonEating() }.shouldEat() -> {
                    eatTask = eat()
                    eatTask?.finally {
                        eatTask = null
                    }?.execute(this@BuildTask)
                    return@listen
                }
                eatTask != null -> return@listen
            }

            if (blueprint is TickingBlueprint) {
                blueprint.tick() ?: failure("Failed to tick the ticking blueprint")
            }

            if (collectDrops()) return@listen

            runSafeAutomated { simulateAndProcess() }
        }

        listen<TickEvent.Post> {
            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listen
            }
        }
    }

    private fun AutomatedSafeContext.simulateAndProcess() {
        val results = runSafeAutomated { blueprint.structure.simulate() }

        DEFAULT.drawables = results
            .filterIsInstance<Drawable>()
            .plus(pendingInteractions.toList())

        val viableResults = results
            .filter { result ->
                val finalResult = (result as? Dependent)?.lastDependency ?: result
                pendingInteractions.none { it.blockPos == finalResult.pos } &&
                        (finalResult !is Contextual ||
                        when (finalResult) {
                            is BreakResult -> buildConfig.breakBlocks
                            else -> buildConfig.interactBlocks
                        })
            }
            .sorted()

        val bestResult = viableResults.firstOrNull() ?: return
        handleResult(bestResult, viableResults)
    }

    private fun AutomatedSafeContext.handleResult(result: BuildResult, allResults: List<BuildResult>) {
        if (result !is Dependent && result !is Contextual && pendingInteractions.isNotEmpty()) return

        when (result) {
            is PreSimResult.Done,
            is PreSimResult.Unbreakable,
            is PreSimResult.Restricted,
            is PreSimResult.NoPermission,
            is GenericResult.Ignored -> {
                if (iteratePropagating()) {
                    simulateAndProcess()
                    return
                }

                if (finishOnDone) success(blueprint.structure)
            }

            is GenericResult.NotVisible,
            is InteractResult.NoIntegrity -> {
                if (!buildConfig.pathing) return
                val sim = blueprint.simulation()
                val goal = BuildGoal(sim, player.blockPos)
                BaritoneManager.setGoalAndPath(goal)
            }

            is Navigable -> {
                if (buildConfig.pathing) BaritoneManager.setGoalAndPath(result.goal)
            }

            is Contextual -> {
                if (atMaxPendingInteractions) return
                when (result) {
                    is BreakResult.Break ->
                        allResults.breakRequest(pendingInteractions) {
                            onStop { breaks++ }
                            onItemDrop?.let { onItemDrop ->
                                onItemDrop { onItemDrop(it) }
                            }
                        }?.submit()

                    is InteractResult.Interact -> {
	                    allResults.interactRequest(pendingInteractions, false) {
		                    onPlace { placements++ }
	                    }?.submit()
                    }
                }
            }

            is Dependent -> handleResult(result.lastDependency, allResults)

            is Resolvable -> {
	            LOG.info("Resolving: ${result.name}")
                result.resolve()
            }
        }
    }

    private fun SafeContext.collectDrops() =
        dropsToCollect
            .firstOrNull()
            ?.let { itemDrop ->
                if (pendingInteractions.isNotEmpty()) return@let true

                if (!world.entities.contains(itemDrop)) {
                    dropsToCollect.remove(itemDrop)
                    BaritoneManager.cancel()
                    return@let true
                }

                if (player.hotbarAndInventoryStacks.none { it.isEmpty }) {
                    val stackToThrow = player.currentScreenHandler.playerSlots.firstOrNull {
                        it.stack.item in inventoryConfig.disposables
                    } ?: run {
                        failure("No item in inventory to throw but inventory is full and cant pick up item drop")
                        return@let true
                    }
                    inventoryRequest {
                        throwStack(stackToThrow.id)
                    }.submit()
                    return@let true
                }

                BaritoneManager.setGoalAndPath(GoalBlock(itemDrop.blockPos))
                return@let true
            } ?: false

    fun iteratePropagating() =
        if (blueprint is PropagatingBlueprint) {
            blueprint.next() ?: failure("Failed to propagate the next blueprint")
            true
        } else false

    companion object {
        @Ta5kBuilder
        fun Automated.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = buildConfig.collectDrops,
            lifeMaintenance: Boolean = false,
            blueprint: () -> Blueprint
        ) = BuildTask(blueprint(), finishOnDone, collectDrops, lifeMaintenance, this)

        @Ta5kBuilder
        context(automated: Automated)
        fun Structure.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = automated.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false
        ) = BuildTask(toBlueprint(), finishOnDone, collectDrops, lifeMaintenance, automated)

        @Ta5kBuilder
        context(automated: Automated)
        fun Blueprint.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = automated.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false
        ) = BuildTask(this, finishOnDone, collectDrops, lifeMaintenance, automated)

        @Ta5kBuilder
        fun Automated.breakAndCollectBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            lifeMaintenance: Boolean = false
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, true, lifeMaintenance, this
        )
    }
}
