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

import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalNear
import com.lambda.Lambda.LOG
import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.InventoryConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.result.Navigable
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.transfer.TransactionExecutor.Companion.transfer
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.Formatting.string
import com.lambda.util.extension.Structure
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import com.lambda.util.world.toFastVec
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

class BuildTask @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val collectDrops: Boolean = TaskFlowModule.build.collectDrops,
    private val build: BuildConfig = TaskFlowModule.build,
    private val rotation: RotationConfig = TaskFlowModule.rotation,
    private val interact: InteractionConfig = TaskFlowModule.interact,
    private val inventory: InventoryConfig = TaskFlowModule.inventory,
    private val hotbar: HotbarConfig = TaskFlowModule.hotbar,
) : Task<Unit>() {
    override val name: String get() = "Building $blueprint with ${(breaks / (age / 20.0 + 0.001)).string} b/s ${(placements / (age / 20.0 + 0.001)).string} p/s"

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()
    private val emptyPendingInteractionSlots
        get() = (build.maxPendingInteractions - pendingInteractions.size).coerceAtLeast(0)
    private val atMaxPendingInteractions
        get() = pendingInteractions.size >= build.maxPendingInteractions

    private var placements = 0
    private var breaks = 0
    private val dropsToCollect = mutableSetOf<ItemEntity>()
//    private var goodPositions = setOf<BlockPos>()

    private val onItemDrop: ((item: ItemEntity) -> Unit)?
        get() = if (collectDrops) {
            item -> dropsToCollect.add(item)
        } else null

    override fun SafeContext.onStart() {
        (blueprint as? PropagatingBlueprint)?.next()
    }

    init {
        listen<TickEvent.Pre> {
            if (collectDrops()) return@listen

            val results = blueprint.simulate(player.eyePos, interact, rotation, inventory, build)
            val resultPostions = results.map { it.blockPos }

            val sim = blueprint.simulation(interact, rotation, inventory, build)
            BlockPos.iterateOutwards(player.blockPos, 2, 2, 2).forEach {
                sim.simulate(it.toFastVec())
            }
            val bestPos = sim.goodPositions()
                .filter { it.pos !in resultPostions }
                .maxByOrNull { it.interactions }

            val drawables = results
                .filterIsInstance<Drawable>()
                .plus(pendingInteractions.toList())
                .toMutableList()

            if (bestPos != null) {
                drawables.add(bestPos)
            }
            TaskFlowModule.drawables = drawables

            val resultsNotBlocked = results
                .filter { result -> pendingInteractions.none { it.expectedPos == result.blockPos } }
                .sorted()

            val bestResult = resultsNotBlocked.firstOrNull() ?: return@listen
            if (bestResult !is BuildResult.Contextual && pendingInteractions.isNotEmpty())
                return@listen
            when (bestResult) {
                is BuildResult.Done,
                is BuildResult.Ignored,
                is BuildResult.Unbreakable,
                is BuildResult.Restricted,
                is BuildResult.NoPermission -> {
                    if (blueprint is PropagatingBlueprint) {
                        blueprint.next()
                        return@listen
                    }

                    if (finishOnDone) success()
                }

                is BuildResult.NotVisible,
                is PlaceResult.NoIntegrity -> {
                    if (!build.pathing) return@listen
                    val goal = BuildGoal(sim, player.blockPos)
                    BaritoneUtils.setGoalAndPath(goal)
                }

                is Navigable -> {
                    if (build.pathing) BaritoneUtils.setGoalAndPath(bestResult.goal)
                }

                is BuildResult.Contextual -> {
                    bestPos?.let {
                        BaritoneUtils.setGoalAndPath(GoalNear(it.pos, 1))
                    }

                    if (atMaxPendingInteractions) return@listen
                    when (bestResult) {
                        is BreakResult.Break -> {
                            val breakResults = resultsNotBlocked.filterIsInstance<BreakResult.Break>()
                            val requestContexts = arrayListOf<BreakContext>()

                            if (build.breakSettings.instantBreaksPerTick > 1) {
                                val takeCount = build.breakSettings
                                    .instantBreaksPerTick
                                    .coerceAtMost(emptyPendingInteractionSlots)
                                breakResults
                                    .filter { it.context.instantBreak }
                                    .take(takeCount)
                                    .let { instantBreakResults ->
                                        requestContexts.addAll(instantBreakResults.map { it.context })
                                    }
                            }

                            if (requestContexts.isEmpty()) {
                                requestContexts.addAll(breakResults.map { it.context })
                            }

                            val request = BreakRequest(
                                requestContexts, build, rotation, interact, inventory, hotbar,
                                pendingInteractionsList = pendingInteractions,
                                onBreak = { breaks++ },
                                onItemDrop = onItemDrop
                            )
                            build.breakSettings.request(request)
                            return@listen
                        }
                        is PlaceResult.Place -> {
                            val placeResults = resultsNotBlocked
                                .filterIsInstance<PlaceResult.Place>()
                                .distinctBy { it.blockPos }
                                .take(emptyPendingInteractionSlots)

                            build.placeSettings.request(
                                PlaceRequest(
                                    placeResults.map { it.context }, build, rotation, hotbar, interact, pendingInteractions
                                ) { placements++ }
                            )
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
            (blueprint as? TickingBlueprint)?.tick()

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
                if (pendingInteractions.isNotEmpty()) return true

                if (!world.entities.contains(itemDrop)) {
                    dropsToCollect.remove(itemDrop)
                    BaritoneUtils.cancel()
                    return true
                }

                val noInventorySpace = player.hotbarAndStorage.none { it.isEmpty }
                if (noInventorySpace) {
                    val stackToThrow = player.currentScreenHandler.inventorySlots.firstOrNull {
                        it.stack.item.block in TaskFlowModule.inventory.disposables
                    } ?: run {
                        failure("No item in inventory to throw but inventory is full and cant pick up item drop")
                        return true
                    }
                    transfer(player.currentScreenHandler) {
                        throwStack(stackToThrow.id)
                    }.execute(this@BuildTask)
                    return true
                }

                BaritoneUtils.setGoalAndPath(GoalBlock(itemDrop.blockPos))
                return true
            } ?: false

    companion object {
        @Ta5kBuilder
        fun build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            inventory: InventoryConfig = TaskFlowModule.inventory,
            blueprint: () -> Blueprint,
        ) = BuildTask(blueprint(), finishOnDone, collectDrops, build, rotation, interact, inventory)

        @Ta5kBuilder
        fun Structure.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            inventory: InventoryConfig = TaskFlowModule.inventory,
        ) = BuildTask(toBlueprint(), finishOnDone, collectDrops, build, rotation, interact, inventory)

        @Ta5kBuilder
        fun Blueprint.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            inventory: InventoryConfig = TaskFlowModule.inventory,
        ) = BuildTask(this, finishOnDone, collectDrops, build, rotation, interact, inventory)

        @Ta5kBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = true,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            inventory: InventoryConfig = TaskFlowModule.inventory,
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, build, rotation, interact, inventory
        )

        @Ta5kBuilder
        fun breakBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            inventory: InventoryConfig = TaskFlowModule.inventory,
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, build, rotation, interact, inventory
        )
    }
}
