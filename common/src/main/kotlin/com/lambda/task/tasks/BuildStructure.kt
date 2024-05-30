package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerManager.findBestAvailableTool
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.mostCenter
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.Communication.info
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.OperatorBlock
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

class BuildStructure(
    private val blueprint: Blueprint,
    private val collectDrops: Boolean = false,
    private val skipWeakBlocks: Boolean = false,
    private val pathing: Boolean = true,
    private val finishOnDone: Boolean = true,
    private val instantAtOnce: Boolean = true,
    private val limitPerTick: Int = 15,
) : Task<Unit>() {
    private var lastResult: BuildResult? = null

    init {
        listener<TickEvent.Pre> {
            val structure = when (blueprint) {
                is DynamicBlueprint -> blueprint.update(this)
                else -> blueprint.structure
            }

            if (finishOnDone && structure.isEmpty()) {
                failure("Structure is empty")
                return@listener
            }

            if (finishOnDone && blueprint.isDone(this)) {
                this@BuildStructure.info("Structure is done")
                success(Unit)
                return@listener
            }

            val results = structure.entries.fold(mutableSetOf<BuildResult>()) { acc, (pos, target) ->
                checkRequirements(pos, target)?.let {
                    acc.add(it)
                    return@fold acc
                }
//                checkPlaceResults(pos, target).let {
//                    if (it.isEmpty()) return@let
//                    acc.addAll(it)
//                    return@fold acc
//                }
                checkBreakResults(pos, target).let {
                    if (it.isEmpty()) return@let
                    acc.addAll(it)
                    return@fold acc
                }
                acc
            }

            val instantResults = results.filterIsInstance<BreakResult.Success>()
                .filter { it.context.instantBreak }
                .take(limitPerTick)

            if (instantAtOnce && instantResults.isNotEmpty()) {
                cancelSubTasks()
                instantResults.forEach {
                    it.resolve.start(this@BuildStructure, false)
                }
                lastResult = instantResults.last()
                return@listener
            }

            val res = results.sorted()
            res

            results.minOrNull()?.let { result ->
                if (lastResult == result) return@listener
                if (result !is Resolvable) return@listener

                lastResult = result
                cancelSubTasks()
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

        val state = pos.blockState(world)
        if (target is TargetState.Air || !state.isReplaceable) return acc

        return acc
    }

    private fun SafeContext.checkBreakResults(pos: BlockPos, target: TargetState): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val state = pos.blockState(world)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (skipWeakBlocks && state.block.hardness == 0f && !state.isAir) {
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
        val currentCast = currentRotation.rayCast(
            interact.reach,
            interact.rayCastMask,
            eye
        )

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
            // ToDo: Verify needed resolution
            scanVisibleSurfaces(eye, box, emptySet(), 2) { vec ->
                if (eye distSq vec > reachSq) {
                    acc.add(BreakResult.OutOfReach(pos, eye.distanceTo(vec)))
                    return@scanVisibleSurfaces
                }
                val cast = eye.rotationTo(vec).rayCast(
                    interact.reach,
                    interact.rayCastMask,
                    eye
                ) ?: return@scanVisibleSurfaces
                if (!cast.verify()) return@scanVisibleSurfaces

                validHits[vec] = cast
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
                var added = false
                Hand.entries.forEach {
                    val stack = player.getStackInHand(it)
                    if (stack.isEmpty) return@forEach
                    if (stack.item == bestTool) return@forEach
                    added = true
                    acc.add(BreakResult.WrongTool(pos, breakContext, bestTool))
                }
                if (added) return acc
            }

            acc.add(BreakResult.Success(pos, breakContext))
        }

        return acc
    }

    companion object {
        @Ta5kBuilder
        fun buildStructure(
            collectDrops: Boolean = false,
            skipWeakBlocks: Boolean = false,
            pathing: Boolean = true,
            finishOnDone: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                collectDrops,
                skipWeakBlocks,
                pathing,
                finishOnDone
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