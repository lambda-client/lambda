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
import com.lambda.Lambda.LOG
import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.InventoryConfig
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.*
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.transfer.TransactionExecutor.Companion.transfer
import com.lambda.interaction.request.ManagerUtils.positionBlockingManagers
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.Communication.info
import com.lambda.util.Formatting.string
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.extension.Structure
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

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

    private val pendingInteractions = LimitedDecayQueue<BuildContext>(
        build.maxPendingInteractions, build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.expectedPos.toShortString()} timed out") }

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

    override fun SafeContext.onCancel() {
//        currentInteraction?.let { ctx ->
//            if (ctx !is BreakContext) return
//            if (ctx.buildConfig.breakSettings.breakingTexture) {
//                setBreakingTextureStage(ctx, -1)
//            }
//        }
    }

    init {
        onRotate {
            if (collectDrops()) return@onRotate

            // ToDo: Simulate for each pair player positions that work
            val results = blueprint.simulate(player.eyePos, interact, rotation, inventory, build)

            TaskFlowModule.drawables = results.filterIsInstance<Drawable>()
                .plus(pendingInteractions.toList())
            //                .plus(sim.goodPositions())

            val resultsNotBlocked = results.filter { result ->
                    when(result) {
                        is BreakResult.Break -> { BreakManager.blockedPositions.none { blocked -> blocked == result.blockPos } }
                        is PlaceResult.Place -> { PlaceManager.blockedPositions.none { blocked -> blocked == result.blockPos } }
                        else -> true
                    }
                }
                .sorted()

            if (build.breakSettings.breaksPerTick > 1) {
                val instantResults = resultsNotBlocked.filterIsInstance<BreakResult.Break>()
                    .filter { it.context.instantBreak }
                    .take(build.breakSettings.breaksPerTick)

                if (instantResults.isNotEmpty()) {
                    build.breakSettings.request(
                        BreakRequest(
                            instantResults.map { it.context }, build, rotation, hotbar,
                            onBreak = { breaks++ },
                            onItemDrop = onItemDrop
                        )
                    )
                    return@onRotate
                }
            }

            val bestResult = resultsNotBlocked.firstOrNull() ?: return@onRotate
            when (bestResult) {
                is BuildResult.Done,
                is BuildResult.Ignored,
                is BuildResult.Unbreakable,
                is BuildResult.Restricted,
                is BuildResult.NoPermission -> {
                    if (pendingInteractions.isNotEmpty()) return@onRotate
                    if (blueprint is PropagatingBlueprint) {
                        blueprint.next()
                        return@onRotate
                    }

                    val managersStillRunning = positionBlockingManagers.any { it.blockedPositions.isNotEmpty() }
                    if (finishOnDone && !managersStillRunning) success()
                }

                is BuildResult.NotVisible,
                is PlaceResult.NoIntegrity -> {
                    if (!build.pathing) return@onRotate
                    val sim = blueprint.simulation(interact, rotation, inventory, build)
                    val goal = BuildGoal(sim, player.blockPos)
                    BaritoneUtils.setGoalAndPath(goal)
                }

                is Navigable -> {
                    if (build.pathing) BaritoneUtils.setGoalAndPath(bestResult.goal)
                }

                is BuildResult.Contextual -> {
                    if (pendingInteractions.size >= build.maxPendingInteractions) return@onRotate
                    when (bestResult) {
                        is BreakResult.Break -> {
                            val breakContexts = resultsNotBlocked
                                .filterIsInstance<BreakResult.Break>()
                                .map { it.context }

                            val request = BreakRequest(
                                breakContexts, build, rotation, hotbar,
                                onBreak = { breaks++ },
                                onItemDrop = onItemDrop
                            )
                            build.breakSettings.request(request)
                            return@onRotate
                        }
                        is PlaceResult.Place -> {
                            build.placeSettings.request(
                                PlaceRequest(bestResult.context, build, rotation, hotbar, interact) { placements++ }
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
                if (positionBlockingManagers.any { it.blockedPositions.isNotEmpty() }) return true

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
