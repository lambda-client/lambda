package com.lambda.task.tasks

import baritone.api.pathing.goals.GoalNear
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerManager.findBestAvailableTool
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.mostCenter
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils.primary
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.Communication.info
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.primitives.extension.Structure
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.OperatorBlock
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

class BuildStructure @Ta5kBuilder constructor(
    private val blueprint: Blueprint,
    private val finishOnDone: Boolean = true,
    private val collectDrops: Boolean = TaskFlow.buildSettings.collectDrops,
    private val breakWeakBlocks: Boolean = TaskFlow.buildSettings.breakWeakBlocks,
    private val pathing: Boolean = TaskFlow.buildSettings.pathing,
    private val interactLimit: Int = TaskFlow.buildSettings.interactLimit,
    private val instantAtOnce: Boolean = TaskFlow.buildSettings.breakInstantAtOnce,
    private val useRayCast: Boolean = TaskFlow.interactionSettings.useRayCast,
) : Task<Unit>() {
    private var lastTask: Task<*>? = null
    private var doneBlueprint: Structure? = null

    override fun SafeContext.onStart() {
        (blueprint as? DynamicBlueprint)?.create(this)
    }

    init {
        listener<TickEvent.Pre> {
            (blueprint as? DynamicBlueprint)?.onTick(this)

            if (finishOnDone && blueprint.structure.isEmpty()) {
                failure("Structure is empty")
                return@listener
            }

            doneBlueprint?.entries?.take(3)?.lastOrNull()?.let {
                primary.customGoalProcess.setGoalAndPath(GoalNear(it.key.up(), 0))
            }

            if (finishOnDone && blueprint.isDone(this)) {
                doneBlueprint = blueprint.structure
                if (blueprint is DynamicBlueprint) {
                    if (!blueprint.onDone(this)) {
                        return@listener
                    }
                }

                this@BuildStructure.info("Structure is done")
                cancelSubTasks()
                success(Unit)
                return@listener
            }

            val results = blueprint.structure.entries.fold(mutableSetOf<BuildResult>()) { acc, (pos, target) ->
                checkRequirements(pos, target)?.let {
                    acc.add(it)
                    return@fold acc
                }
                checkPlaceResults(pos, target).let {
                    if (it.isEmpty()) return@let
                    acc.addAll(it)
                    return@fold acc
                }
                checkBreakResults(pos).let {
                    if (it.isEmpty()) return@let
                    acc.addAll(it)
                    return@fold acc
                }
                acc
            }

            val instantResults = results.filterIsInstance<BreakResult.Success>()
                .filter { it.context.instantBreak }
                .take(interactLimit)

            if (instantAtOnce && instantResults.isNotEmpty()) {
                cancelSubTasks()
                instantResults.forEach {
                    it.resolve.start(this@BuildStructure, false)
                }
                lastTask = instantResults.last().resolve
                return@listener
            }

            val res = results.sorted()
            res

            results.minOrNull()?.let { result ->
                if (result !is Resolvable) return@listener
                if (lastTask?.isCompleted == false) return@listener

                lastTask = result.resolve
                cancelSubTasks()

                if (!pathing && result is BuildResult.OutOfReach) return@let
                result.resolve.start(this@BuildStructure, false)
            }
        }
    }

    private fun SafeContext.checkRequirements(pos: BlockPos, target: TargetState): BuildResult? {
        /* the chunk is not loaded */
        if (!world.isChunkLoaded(pos)) {
            return BuildResult.ChunkNotLoaded(pos)
        }

        val state = pos.blockState(world)

        /* block is already in the correct state */
        if (target.matches(state, pos, world)) {
            return BuildResult.Done(pos)
        }

        /* block should be ignored */
        if (state.block in TaskFlow.ignoredBlocks) {
            return BuildResult.Ignored(pos)
        }

        /* the player is in the wrong game mode to alter the block state */
        if (player.isBlockBreakingRestricted(world, pos, interaction.currentGameMode)) {
            return BuildResult.Restricted(pos)
        }

        /* the player has no permissions to alter the block state */
        if (state.block is OperatorBlock && !player.isCreativeLevelTwoOp) {
            return BuildResult.NoPermission(pos, state)
        }

        /* block is outside the world so it cant be altered */
        if (!world.worldBorder.contains(pos) || world.isOutOfHeightLimit(pos)) {
            return BuildResult.OutOfWorld(pos)
        }

        /* block is unbreakable, so it cant be broken or replaced */
        if (state.getHardness(world, pos) < 0) {
            return BuildResult.Unbreakable(pos, state)
        }

        return null
    }

    private fun SafeContext.checkPlaceResults(pos: BlockPos, target: TargetState): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()

        if (target is TargetState.Air || !pos.blockState(world).isReplaceable) return acc

        val interact = TaskFlow.interactionSettings
        val rotation = TaskFlow.rotationSettings

        Direction.entries.forEach { neighbor ->
            val hitPos = pos.offset(neighbor)
            val hitSide = neighbor.opposite

            val voxelShape = hitPos.blockState(world).getOutlineShape(world, hitPos)
            val boxes = voxelShape.boundingBoxes.map { it.offset(hitPos) }
            val verify: HitResult.() -> Boolean = {
                blockResult?.blockPos == hitPos && blockResult?.side == hitSide
            }

            val eye = player.getCameraPosVec(mc.tickDelta)
            val validHits = mutableMapOf<Vec3d, HitResult>()
            val reachSq = interact.reach.pow(2)

            boxes.forEach { box ->
                val res = if (useRayCast) interact.resolution else 2
                scanVisibleSurfaces(eye, box, setOf(hitSide), res) { side, vec ->
                    if (eye distSq vec > reachSq) {
                        acc.add(BuildResult.OutOfReach(pos, eye, vec, interact.reach, side))
                        return@scanVisibleSurfaces
                    }

                    validHits[vec] = if (useRayCast) {
                        val cast = eye.rotationTo(vec)
                            .rayCast(interact.reach, eye) ?: return@scanVisibleSurfaces
                        if (!cast.verify()) return@scanVisibleSurfaces

                        cast
                    } else {
                        BlockHitResult(
                            vec,
                            side,
                            hitPos,
                            false
                        )
                    }

                }
            }

            validHits.keys.mostCenter?.let { optimum ->
                validHits.minByOrNull { optimum distSq it.key }?.let { closest ->
                    val optimumRotation = eye.rotationTo(closest.key)
                    RotationContext(optimumRotation, rotation, closest.value, verify)
                }
            }?.let { rotation ->
                val optimalStack = target.getStack(world, pos)

                val usageContext = ItemUsageContext(
                    player,
                    Hand.MAIN_HAND, // ToDo: notice that the hand may have a different item stack and simulation will be wrong
                    rotation.hitResult?.blockResult,
                )
                val cachePos = CachedBlockPosition(
                    usageContext.world,
                    usageContext.blockPos,
                    false
                )
                val canBePlacedOn = optimalStack.canPlaceOn(
                    usageContext.world.registryManager.get(RegistryKeys.BLOCK),
                    cachePos,
                )
                if (!player.abilities.allowModifyWorld && !canBePlacedOn) {
                    acc.add(PlaceResult.IllegalUsage(pos))
                    return@forEach
                }

                var context = ItemPlacementContext(usageContext)

                if (!optimalStack.item.isEnabled(world.enabledFeatures)) {
                    acc.add(PlaceResult.BlockFeatureDisabled(pos, optimalStack))
                    return@forEach
                }

                if (!context.canPlace()) {
                    acc.add(PlaceResult.CantReplace(pos, context))
                    return@forEach
                }

                val blockItem = optimalStack.item as? BlockItem ?: run {
                    acc.add(PlaceResult.NotItemBlock(pos, optimalStack))
                    return@forEach
                }

                val checked = blockItem.getPlacementContext(context)
                if (checked == null) {
                    acc.add(PlaceResult.ScaffoldExceeded(pos, context))
                    return@forEach
                } else {
                    context = checked
                }

                val resultState = blockItem.getPlacementState(context) ?: run {
                    acc.add(PlaceResult.CantReplace(pos, context))
                    return@forEach
                }

                if (!target.matches(resultState, pos, world)) {
                    acc.add(PlaceResult.NoIntegrity(pos, resultState, context))
                    return@forEach
                }

                val blockHit = rotation.hitResult?.blockResult ?: return@forEach
                val hitBlock = blockHit.blockPos.blockState(world).block
                val shouldSneak = hitBlock in BlockUtils.interactionBlacklist

                val placeContext = PlaceContext(
                    eye,
                    blockHit,
                    rotation,
                    eye.distanceTo(blockHit.pos),
                    resultState,
                    blockHit.blockPos.blockState(world),
                    target,
                    Hand.MAIN_HAND,
                    shouldSneak,
                    false
                )

                if (optimalStack.item != player.getStackInHand(placeContext.hand).item) {
                    acc.add(BuildResult.WrongItem(pos, placeContext, optimalStack.item))
                    return@forEach
                }

                acc.add(PlaceResult.Success(pos, placeContext))
            }
        }

        return acc
    }

    private fun SafeContext.checkBreakResults(pos: BlockPos): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val state = pos.blockState(world)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (breakWeakBlocks && state.block.hardness == 0f && !state.isAir) {
            acc.add(BuildResult.Ignored(pos))
            return acc
        }

        /* player is standing on top of the block */
        val pBox = player.boundingBox
        val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
        world.findSupportingBlockPos(player, aabb).orElse(null)?.let { support ->
            if (support != pos) return@let
            acc.add(BreakResult.PlayerOnTop(pos, state))
            return acc
        }

        /* liquid needs to be submerged first to be broken */
        if (!state.fluidState.isEmpty && state.isReplaceable) {
            val submerge = checkPlaceResults(pos, TargetState.Solid)
            acc.add(BreakResult.Submerge(pos, state, submerge))
            acc.addAll(submerge)
            return acc
        }

        val adjacentLiquids = Direction.entries.filter {
            it != Direction.DOWN && !pos.offset(it).blockState(world).fluidState.isEmpty
        }

        /* block has liquids next to it that will leak when broken */
        if (adjacentLiquids.isNotEmpty()) {
            acc.add(BreakResult.BlockedByLiquid(pos, state))
            adjacentLiquids.forEach {
                val submerge = checkPlaceResults(pos.offset(it), TargetState.Solid)
                acc.addAll(submerge)
            }
            return acc
        }

        /* The current selected item cant mine the block */
        Hand.entries.forEach {
            val stack = player.getStackInHand(it)
            if (stack.isEmpty) return@forEach
            if (stack.item.canMine(state, world, pos, player)) return@forEach
            acc.add(BreakResult.ItemCantMine(pos, state, stack.item))
            return acc
        }

        val eye = player.getCameraPosVec(mc.tickDelta)

        val interact = TaskFlow.interactionSettings
        val rotation = TaskFlow.rotationSettings
        val currentRotation = RotationManager.currentRotation
        val currentCast = currentRotation.rayCast(interact.reach, eye)

        val voxelShape = state.getOutlineShape(world, pos)
        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }
        val verify: HitResult.() -> Boolean = { blockResult?.blockPos == pos }

        /* the player is buried inside the block */
        if (boxes.any { it.contains(eye) }) {
            currentCast?.blockResult?.let { blockHit ->
                val rotationContext = RotationContext(currentRotation, rotation, currentCast, verify)
                val breakContext = BreakContext(
                    eye,
                    blockHit,
                    rotationContext,
                    state,
                    player.activeHand,
                    instantBreakable(state, pos)
                )
                acc.add(BreakResult.Success(pos, breakContext))
                return acc
            }
        }

        val validHits = mutableMapOf<Vec3d, HitResult>()
        val reachSq = interact.reach.pow(2)

        boxes.forEach { box ->
            val res = if (useRayCast) interact.resolution else 2
            scanVisibleSurfaces(eye, box, emptySet(), res) { side, vec ->
                if (eye distSq vec > reachSq) {
                    acc.add(BuildResult.OutOfReach(pos, eye, vec, interact.reach, side))
                    return@scanVisibleSurfaces
                }

                validHits[vec] = if (useRayCast) {
                    val cast = eye.rotationTo(vec)
                        .rayCast(interact.reach, eye) ?: return@scanVisibleSurfaces
                    if (!cast.verify()) return@scanVisibleSurfaces

                    cast
                } else {
                    BlockHitResult(
                        vec,
                        side,
                        pos,
                        false
                    )
                }
            }
        }

        validHits.keys.mostCenter?.let { optimum ->
            validHits.minByOrNull { optimum distSq it.key }?.let { closest ->
                val optimumRotation = eye.rotationTo(closest.key)
                RotationContext(optimumRotation, rotation, closest.value, verify)
            }
        }?.let { bestRotation ->
            val blockHit = bestRotation.hitResult?.blockResult ?: return@let

            val breakContext = BreakContext(
                eye,
                blockHit,
                bestRotation,
                state,
                player.activeHand,
                instantBreakable(state, pos)
            )

            /* player has a better tool for the job available */
            findBestAvailableTool(state)?.let { bestTool ->
                Hand.entries.firstOrNull {
                    val stack = player.getStackInHand(it)
                    stack.item == bestTool
                }?.let { hand ->
                    breakContext.hand = hand
                    acc.add(BreakResult.Success(pos, breakContext))
                    return acc
                } ?: run {
                    acc.add(BuildResult.WrongItem(pos, breakContext, bestTool))
                    return acc
                }
            }

            acc.add(BreakResult.Success(pos, breakContext))
        }

        return acc
    }

    companion object {
        @Ta5kBuilder
        fun buildStructure(
            finishOnDone: Boolean = true,
            collectDrops: Boolean = TaskFlow.buildSettings.collectDrops,
            breakWeakBlocks: Boolean = TaskFlow.buildSettings.breakWeakBlocks,
            pathing: Boolean = TaskFlow.buildSettings.pathing,
            interactLimit: Int = TaskFlow.buildSettings.interactLimit,
            instantAtOnce: Boolean = TaskFlow.buildSettings.breakInstantAtOnce,
            useRayCast: Boolean = TaskFlow.interactionSettings.useRayCast,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                finishOnDone,
                collectDrops,
                breakWeakBlocks,
                pathing,
                interactLimit,
                instantAtOnce,
                useRayCast
            )

        @Ta5kBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos,
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
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