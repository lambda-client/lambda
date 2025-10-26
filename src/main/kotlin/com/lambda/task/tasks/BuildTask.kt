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
import com.lambda.config.groups.EatConfig.Companion.reasonEating
import com.lambda.context.Automated
import com.lambda.context.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.InteractResult
import com.lambda.interaction.construction.result.Navigable
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.transfer.TransactionExecutor.Companion.transfer
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.interaction.request.interacting.InteractRequest
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.task.Task
import com.lambda.task.tasks.EatTask.Companion.eat
import com.lambda.threading.runSafeAutomated
import com.lambda.util.Communication.info
import com.lambda.util.Formatting.string
import com.lambda.util.extension.Structure
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.hotbarAndStorage
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
    override val name: String get() = "Building $blueprint with ${(breaks / (age / 20.0 + 0.001)).string} b/s ${(placements / (age / 20.0 + 0.001)).string} p/s"

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()
    private val atMaxPendingInteractions
        get() = pendingInteractions.size >= buildConfig.maxPendingInteractions

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

            val results = runSafeAutomated { blueprint.simulate(player.eyePos) }

            AutomationConfig.drawables = results
                .filterIsInstance<Drawable>()
                .plus(pendingInteractions.toList())

            val resultsNotBlocked = results
                .filter { result -> pendingInteractions.none { it.blockPos == result.blockPos } }
                .sorted()

            val bestResult = resultsNotBlocked.firstOrNull() ?: return@listen
            if (bestResult !is BuildResult.Contextual && pendingInteractions.isNotEmpty())
                return@listen
            info("Best result: $bestResult")
            when (bestResult) {
                is BuildResult.Done,
                is BuildResult.Ignored,
                is BuildResult.Unbreakable,
                is BuildResult.Restricted,
                is BuildResult.NoPermission -> {
                    if (iteratePropagating()) return@listen

                    if (finishOnDone) success(blueprint.structure)
                }

                is BuildResult.NotVisible,
                is PlaceResult.NoIntegrity -> {
                    if (!buildConfig.pathing) return@listen
                    val sim = blueprint.simulation()
                    val goal = BuildGoal(sim, player.blockPos)
                    BaritoneManager.setGoalAndPath(goal)
                }

                is Navigable -> {
                    if (buildConfig.pathing) BaritoneManager.setGoalAndPath(bestResult.goal)
                }

                is BuildResult.Contextual -> {
                    if (atMaxPendingInteractions) return@listen
                    when (bestResult) {
                        is BreakResult.Break -> {
                            val breakResults = resultsNotBlocked
                                .filterIsInstance<BreakResult.Break>()
                                .map { it.context }

                            breakRequest(breakResults, pendingInteractions) {
                                onStop { breaks++ }
                                onItemDrop?.let { onItemDrop ->
                                    onItemDrop { onItemDrop(it) }
                                }
                            }.submit()
                            return@listen
                        }
                        is PlaceResult.Place -> {
                            val placeResults = resultsNotBlocked
                                .filterIsInstance<PlaceResult.Place>()
                                .map { it.context }

                            PlaceRequest(
                                placeResults,
                                pendingInteractions,
                                this@BuildTask
                            ) { placements++ }.submit()
                        }
                        is InteractResult.Interact -> {
                            val interactResults = resultsNotBlocked
                                .filterIsInstance<InteractResult.Interact>()
                                .map { it.context }

                            InteractRequest(
                                interactResults,
                                pendingInteractions,
                                this@BuildTask,
                                null
                            ).submit()
                        }
                    }
                }

                is Resolvable -> {
                    LOG.info("Resolving: ${bestResult.name}")

                    bestResult.resolve().execute(this@BuildTask)
                }
            }
        }

        listen<TickEvent.Post> {
            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listen
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

                if (player.hotbarAndStorage.none { it.isEmpty }) {
                    val stackToThrow = player.currentScreenHandler.inventorySlots.firstOrNull {
                        it.stack.item.block in inventoryConfig.disposables
                    } ?: run {
                        failure("No item in inventory to throw but inventory is full and cant pick up item drop")
                        return@let true
                    }
                    transfer(player.currentScreenHandler) {
                        throwStack(stackToThrow.id)
                    }.execute(this@BuildTask)
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
            collectDrops: Boolean = AutomationConfig.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false,
            blueprint: () -> Blueprint
        ) = BuildTask(blueprint(), finishOnDone, collectDrops, lifeMaintenance, this)

        @Ta5kBuilder
        context(automated: Automated)
        fun Structure.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = AutomationConfig.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false
        ) = BuildTask(toBlueprint(), finishOnDone, collectDrops, lifeMaintenance, automated)

        @Ta5kBuilder
        context(automated: Automated)
        fun Blueprint.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = AutomationConfig.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false
        ) = BuildTask(this, finishOnDone, collectDrops, lifeMaintenance, automated)

        @Ta5kBuilder
        fun Automated.breakAndCollectBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = true,
            lifeMaintenance: Boolean = false
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, lifeMaintenance, this
        )

        @Ta5kBuilder
        fun Automated.breakBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = AutomationConfig.buildConfig.collectDrops,
            lifeMaintenance: Boolean = false
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, lifeMaintenance, this
        )
    }
}
