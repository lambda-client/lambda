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
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.*
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.transfer.TransactionExecutor.Companion.transfer
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.Formatting.string
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.extension.Structure
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import net.minecraft.block.BlockState
import net.minecraft.block.OperatorBlock
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

class BuildTask @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val collectDrops: Boolean = TaskFlowModule.build.collectDrops,
    private val build: BuildConfig = TaskFlowModule.build,
    private val rotation: RotationConfig = TaskFlowModule.rotation,
    private val interact: InteractionConfig = TaskFlowModule.interact,
    private val inventory: InventoryConfig = TaskFlowModule.inventory,
) : Task<Unit>() {
    override val name: String get() = "Building $blueprint with ${(breaks / (age / 20.0 + 0.001)).string} b/s ${(placements / (age / 20.0 + 0.001)).string} p/s"

    private val pendingInteractions = LimitedDecayQueue<BuildContext>(
        build.maxPendingInteractions, build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.expectedPos.toShortString()} timed out") }
    private var currentInteraction: BuildContext? = null
    private val instantBreaks = mutableSetOf<BreakContext>()

    var breaking = false
    var breakingTicks= 0
    var soundsCooldown = 0.0f

    private var placements = 0
    private var breaks = 0
    private val dropsToCollect = mutableSetOf<ItemEntity>()
//    private var goodPositions = setOf<BlockPos>()

    override fun SafeContext.onStart() {
        (blueprint as? PropagatingBlueprint)?.next()
    }

    init {
        listen<TickEvent.Pre> {
            val currentItemStack = HotbarManager.mainHandStack ?: return@listen

            currentInteraction?.let { context ->
//                TaskFlowModule.drawables = listOf(context)
                if (context.shouldRotate(build) && !context.rotation.done) return@let
                when (context) {
                    is PlaceContext -> {
                        if (context.sneak && !player.isSneaking) return@let
                        placeBlock(Hand.MAIN_HAND, context)
                    }
                    is BreakContext -> {
                        updateBlockBreakingProgress(context, currentItemStack)
                    }
                }
            }
            instantBreaks.forEach { context ->
                updateBlockBreakingProgress(context, currentItemStack)
                pendingInteractions.add(context)
            }
            instantBreaks.clear()

            dropsToCollect.firstOrNull()?.let { itemDrop ->
                if (!world.entities.contains(itemDrop)) {
                    dropsToCollect.remove(itemDrop)
                    BaritoneUtils.cancel()
                    return@listen
                }

                val noInventorySpace = player.hotbarAndStorage.none { it.isEmpty }
                if (noInventorySpace) {
                    val stackToThrow = player.currentScreenHandler.inventorySlots.firstOrNull {
                        it.stack.item.block in TaskFlowModule.inventory.disposables
                    } ?: run {
                        failure("No item in inventory to throw but inventory is full and cant pick up item drop")
                        return@listen
                    }
                    transfer(player.currentScreenHandler) {
                        throwStack(stackToThrow.id)
                    }.execute(this@BuildTask)
                    return@listen
                }

                BaritoneUtils.setGoalAndPath(GoalBlock(itemDrop.blockPos))
            }
        }

        listen<TickEvent.Post> {
            (blueprint as? TickingBlueprint)?.tick()

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listen
            }
        }

        onRotate {
            if (collectDrops && dropsToCollect.isNotEmpty()) return@onRotate

//            val sim = blueprint.simulation(interact, rotation, inventory)
//            BlockPos.iterateOutwards(player.blockPos, 5, 5, 5).forEach { pos ->
//                sim.simulate(pos.toFastVec())
//            }

            // ToDo: Simulate for each pair player positions that work
            val results = blueprint.simulate(player.eyePos, interact, rotation, inventory, build)

            TaskFlowModule.drawables = results.filterIsInstance<Drawable>()
                .plus(pendingInteractions.toList())
//                .plus(sim.goodPositions())

            if (build.breaksPerTick > 1) {
                val instantResults = results.filterIsInstance<BreakResult.Break>()
                    .filter { it.context.instantBreak }
                    .sorted()
                    .take(build.breaksPerTick)

                instantBreaks.addAll(instantResults.map { it.context })

                if (instantResults.isNotEmpty()) return@onRotate
            }

            val resultsWithoutPending = results.filterNot { result ->
                result.blockPos in pendingInteractions.map { it.expectedPos }
            }
            val bestResult = resultsWithoutPending.minOrNull() ?: return@onRotate
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
                    if (finishOnDone) success()
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

                    currentInteraction = bestResult.context
                }

                is Resolvable -> {
                    LOG.info("Resolving: ${bestResult.name}")

                    bestResult.resolve().execute(this@BuildTask)
                }
            }

            if (!build.rotateForPlace) return@onRotate
            val rotateTo = currentInteraction?.rotation ?: return@onRotate

            rotation.request(rotateTo)
        }

        listen<MovementEvent.InputUpdate> {
            val context = currentInteraction ?: return@listen
            if (context !is PlaceContext) return@listen
            if (context.sneak) it.input.sneaking = true
        }

//        listen<WorldEvent.BlockUpdate.Client> { event ->
//            val context = currentInteraction ?: return@listen
//            if (context.expectedPos != event.pos) return@listen
//            currentInteraction = null
//            pendingInteractions.add(context)
//        }

        listen<WorldEvent.BlockUpdate.Server>(alwaysListen = true) { event ->
            pendingInteractions.firstOrNull { it.expectedPos == event.pos }?.let { ctx ->
                pendingInteractions.remove(ctx)
                if (!ctx.targetState.matches(event.newState, event.pos, world)) {
                    this@BuildTask.warn("Update at ${event.pos.toShortString()} was rejected with ${event.newState} instead of ${ctx.targetState}")
                    return@let
                }
                when (ctx) {
                    is BreakContext -> {
                        if (ctx.buildConfig.breakConfirmation == BuildConfig.BreakConfirmationMode.AwaitThenBreak) {
                            breakBlock(ctx)
                        }
                        breaks++
                    }
                    is PlaceContext -> placements++
                }
            }
        }

        // ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
        listen<EntityEvent.EntityUpdate> {
            if (!collectDrops) return@listen
            if (it.entity !is ItemEntity) return@listen
            pendingInteractions.find { context ->
                val inRange = context.expectedPos.toCenterPos().isInRange(it.entity.pos, 0.5)
                val correctMaterial = context.checkedState.block == it.entity.stack.item.block
                inRange && correctMaterial
            }?.let { context ->
                dropsToCollect.add(it.entity)
            }
        }
    }

    fun SafeContext.placeBlock(hand: Hand, ctx: PlaceContext) {
        with(ctx) {
            val actionResult = interaction.interactBlock(
                player, hand, result
            )

            if (actionResult.isAccepted) {
                if (actionResult.shouldSwingHand() && interact.swingHand) {
                    player.swingHand(hand)
                }

                if (!player.getStackInHand(hand).isEmpty && interaction.hasCreativeInventory()) {
                    mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
                }
            } else {
                warn("Internal interaction failed with $actionResult")
            }
        }
    }

    private fun SafeContext.updateBlockBreakingProgress(ctx: BreakContext, item: ItemStack): Boolean {
        if (interaction.blockBreakingCooldown > 0) {
            interaction.blockBreakingCooldown--
            return true
        }

        val hitResult = ctx.result

        if (interaction.currentGameMode.isCreative && world.worldBorder.contains(ctx.expectedPos)) {
            interaction.blockBreakingCooldown = ctx.buildConfig.breakDelay
            interaction.sendSequencedPacket(world) { sequence: Int ->
                onBlockBreak(ctx)
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
            return true
        }

        if (!breaking) return attackBlock(ctx)

        val blockState = blockState(ctx.expectedPos)
        if (blockState.isAir) return false

        breakingTicks++
        val progress = blockState.calcItemBlockBreakingDelta(
            player,
            world,
            ctx.expectedPos,
            item
        )

        if (ctx.buildConfig.sounds) {
            if (soundsCooldown % 4.0f == 0.0f) {
                val blockSoundGroup = blockState.soundGroup
                mc
                    .soundManager
                    .play(
                        PositionedSoundInstance(
                            blockSoundGroup.hitSound,
                            SoundCategory.BLOCKS,
                            (blockSoundGroup.getVolume() + 1.0f) / 8.0f,
                            blockSoundGroup.getPitch() * 0.5f,
                            SoundInstance.createRandom(),
                            ctx.expectedPos
                        )
                    )
            }
            soundsCooldown++
        }

        if (ctx.buildConfig.particles) {
            mc.particleManager.addBlockBreakingParticles(
                ctx.expectedPos,
                hitResult.side
            )
        }

        if (progress >= ctx.buildConfig.breakThreshold) {
            interaction.sendSequencedPacket(world) { sequence: Int ->
                onBlockBreak(ctx)
                PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, ctx.expectedPos, hitResult.side, sequence)
            }
        }

        if (ctx.buildConfig.breakingTexture) {
            setBreakingTextureStage(ctx)
        }

        return true
    }

    private fun SafeContext.onBlockBreak(ctx: BreakContext) {
        when (ctx.buildConfig.breakConfirmation) {
            BuildConfig.BreakConfirmationMode.None -> {
                breakBlock(ctx)
                breaks++
            }
            BuildConfig.BreakConfirmationMode.BreakThenAwait -> {
                breakBlock(ctx)
                pendingInteractions.add(ctx)
            }
            BuildConfig.BreakConfirmationMode.AwaitThenBreak -> pendingInteractions.add(ctx)
        }
        currentInteraction = null
    }

    private fun SafeContext.breakBlock(ctx: BreakContext): Boolean {
        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, interaction.currentGameMode)) return false

        if (HotbarManager.mainHandStack?.item?.canMine(ctx.checkedState, world, ctx.expectedPos, player) == false)
            return false
        val block = ctx.checkedState.block;
        if (block is OperatorBlock && !player.isCreativeLevelTwoOp) return false
        if (ctx.checkedState.isAir) return false

        block.onBreak(world, ctx.expectedPos, ctx.checkedState, player)
        val fluidState = fluidState(ctx.expectedPos)
        val setState = world.setBlockState(ctx.expectedPos, fluidState.blockState, 11)
        if (setState) block.onBroken(world, ctx.expectedPos, ctx.checkedState)

        if (ctx.buildConfig.breakingTexture) setBreakingTextureStage(ctx, -1)

        return setState
    }

    private fun SafeContext.setBreakingTextureStage(
        ctx: BreakContext,
        stage: Int = ctx.getBlockBreakingProgress(
            breakingTicks,
            player, world
        )
    ) {
        world.setBlockBreakingInfo(
            player.id,
            ctx.expectedPos,
            stage
        )
    }

    private fun SafeContext.attackBlock(ctx: BreakContext): Boolean {
        if (player.isBlockBreakingRestricted(world, ctx.expectedPos, interaction.currentGameMode)) return false
        if (!world.worldBorder.contains(ctx.expectedPos)) return false

        if (interaction.currentGameMode.isCreative) {
            interaction.sendSequencedPacket(world) { sequence: Int ->
                onBlockBreak(ctx)
                PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, ctx.expectedPos, ctx.result.side, sequence)
            }
            interaction.blockBreakingCooldown = 5
            return true
        }
        if (breaking) return false

        val blockState: BlockState = world.getBlockState(ctx.expectedPos)
        var pendingUpdateManager = world.pendingUpdateManager.incrementSequence()
        val sequence = pendingUpdateManager.sequence
        val notAir = !blockState.isAir
        if (notAir && breakingTicks == 0) {
            blockState.onBlockBreakStart(world, ctx.expectedPos, player)
        }

        val currentItemStack = HotbarManager.mainHandStack ?: return false

        if (notAir && blockState.calcItemBlockBreakingDelta(player, world, ctx.expectedPos, currentItemStack) >= build.breakThreshold) {
            onBlockBreak(ctx)
            return true
        } else {
            breaking = true
            soundsCooldown = 0.0f
            if (ctx.buildConfig.breakingTexture) {
                setBreakingTextureStage(ctx)
            }
        }

        if (ctx.buildConfig.breakMode == BuildConfig.BreakMode.Packet) {
            ctx.abortBreakPacket(sequence, connection)
            ctx.stopBreakPacket(sequence + 1, connection)
            ctx.startBreakPacket(sequence + 2, connection)
            ctx.stopBreakPacket(sequence + 3, connection)
            (0..3).forEach { i ->
                pendingUpdateManager.incrementSequence()
            }
        } else {
            ctx.startBreakPacket(sequence, connection)
        }

        return true
    }

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
