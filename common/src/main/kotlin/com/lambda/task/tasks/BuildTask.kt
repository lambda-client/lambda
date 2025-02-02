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
import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.DynamicBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.*
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.Formatting.string
import com.lambda.util.extension.Structure
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

class BuildTask @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val collectDrops: Boolean = TaskFlowModule.build.collectDrops,
    private val build: BuildConfig = TaskFlowModule.build,
    private val rotation: RotationConfig = TaskFlowModule.rotation,
    private val interact: InteractionConfig = TaskFlowModule.interact,
) : Task<Unit>() {
    override val name: String get() = "Building $blueprint with ${(placements / (age / 20.0 + 0.001)).string} p/s"

    private val pendingPlacements = ConcurrentLinkedQueue<PlaceContext>()
    private val pendingBreaks = ConcurrentLinkedQueue<BreakContext>()

    private var currentPlacement: PlaceContext? = null
    private var placements = 0
    private var breaks = 0
    private var inScope = 0

    override fun SafeContext.onStart() {
        (blueprint as? DynamicBlueprint)?.create()
    }

    init {
        listen<TickEvent.Pre> {
            pendingPlacements.removeIf {
                val timeout = (mc.uptimeInTicks - it.placeTick) > build.placeTimeout
                if (timeout) {
                    info("Placement Timeout of ${it.expectedPos.toShortString()}")
                }
                timeout
            }

            currentPlacement?.let { context ->
                if (!context.rotation.done) return@listen
                if (inScope++ < 1) return@listen // ToDo: Should not be needed but timings are wrong
                context.place(interact.swingHand)
                pendingPlacements.add(context)
                currentPlacement = null
                inScope = 0
            }

            (blueprint as? DynamicBlueprint)?.update()

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listen
            }

            // ToDo: Simulate for each pair player positions that work
            val results = blueprint.simulate(player.getCameraPosVec(mc.tickDelta))
            TaskFlowModule.drawables = results.filterIsInstance<Drawable>().plus(pendingPlacements.toList())

            val instantResults = results.filterIsInstance<BreakResult.Break>()
                .filter { it.context.instantBreak }
                .sorted()
                .take(build.breaksPerTick)

            if (build.breaksPerTick > 1 && instantResults.isNotEmpty()) {
                instantResults.forEach {
                    it.resolve().execute(this@BuildTask, pauseParent = false)
                }
                return@listen
            }

            val resultsWithoutPending = results.filterNot { res ->
                val blockedPositions = pendingPlacements.map { it.expectedPos }
                res is PlaceResult.Place && res.context.expectedPos in blockedPositions
            }
            val result = resultsWithoutPending.minOrNull() ?: return@listen
            when (result) {
                is BuildResult.Done,
                is BuildResult.Ignored,
                is BuildResult.Unbreakable,
                is BuildResult.Restricted,
                is BuildResult.NoPermission,
                    -> {
                    if (finishOnDone) success()
                }

                is BuildResult.NotVisible, is PlaceResult.NoIntegrity -> {
                    if (build.pathing) BaritoneUtils.setGoalAndPath(BuildGoal(blueprint.simulation()))
                }

                is Navigable -> {
                    if (build.pathing) BaritoneUtils.setGoalAndPath(result.goal)
                }

                is PlaceResult.Place -> {
                    if (pendingPlacements.size >= build.maxPendingPlacements) return@listen

//                    if (!result.context.rotation.isValid) {
//                        currentPlacement = result.context
//                        return@listen
//                    }

                    currentPlacement = result.context
                }

                is Resolvable -> {
                    LOG.info("Resolving: ${result.name}")

                    if (result is BreakResult.Break) {
                        result.collectDrop = collectDrops
                    }

                    result.resolve().execute(this@BuildTask, pauseParent = result.pausesParent)
                }
            }
        }

        onRotate {
            if (currentPlacement == null) return@onRotate
            if (!build.rotateForPlace) return@onRotate

            rotation.request(
                currentPlacement?.rotation ?: return@onRotate
            )
        }

        listen<MovementEvent.InputUpdate> {
            val context = currentPlacement ?: return@listen
            val hitBlock = context.result.blockPos.blockState(world).block
            if (hitBlock in BlockUtils.interactionBlacklist) {
                it.input.sneaking = true
            }
        }

        listen<PacketEvent.Receive.Pre> { event ->
            val packet = event.packet
            if (packet !is BlockUpdateS2CPacket) return@listen

            pendingPlacements.firstOrNull { it.expectedPos == packet.pos }?.let {
                if (it.targetState.matches(packet.state, packet.pos, world)) {
                    pendingPlacements.remove(it)
                    placements++
                }
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
            blueprint: () -> Blueprint,
        ) = BuildTask(blueprint(), finishOnDone, collectDrops, build, rotation, interact)

        @Ta5kBuilder
        fun Structure.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
        ) = BuildTask(toBlueprint(), finishOnDone, collectDrops, build, rotation, interact)

        @Ta5kBuilder
        fun Blueprint.build(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
        ) = BuildTask(this, finishOnDone, collectDrops, build, rotation, interact)

        @Ta5kBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = true,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, build, rotation, interact
        )

        @Ta5kBuilder
        fun breakBlock(
            blockPos: BlockPos,
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlowModule.build.collectDrops,
            build: BuildConfig = TaskFlowModule.build,
            rotation: RotationConfig = TaskFlowModule.rotation,
            interact: InteractionConfig = TaskFlowModule.interact,
        ) = BuildTask(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            finishOnDone, collectDrops, build, rotation, interact
        )
    }
}
