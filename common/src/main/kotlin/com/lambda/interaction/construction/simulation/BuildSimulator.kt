package com.lambda.interaction.construction.simulation

import com.lambda.context.SafeContext
import com.lambda.interaction.RotationManager
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerManager.findBestAvailableTool
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker
import com.lambda.interaction.visibilty.VisibilityChecker.ScanMode.Companion.scanMode
import com.lambda.interaction.visibilty.VisibilityChecker.optimum
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.modules.client.TaskFlow
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.vecOf
import com.lambda.util.Communication.warn
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.OperatorBlock
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.RegistryKeys
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

object BuildSimulator {
    fun Blueprint.simulate(eye: Vec3d, reach: Double = TaskFlow.interact.reach) =
        runSafe {
            structure.entries.flatMap { (pos, target) ->
                checkRequirements(pos, target)?.let {
                    return@flatMap setOf(it)
                }
                checkPlaceResults(pos, target, eye, reach).let {
                    if (it.isEmpty()) return@let
                    return@flatMap it
                }
                checkBreakResults(pos, eye, reach).let {
                    if (it.isEmpty()) return@let
                    return@flatMap it
                }
                warn("Nothing matched $pos $target")
                emptySet()
            }.toSet()
        } ?: emptySet()

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
        if (state.block in TaskFlow.ignoredBlocks && target.type == TargetState.Type.AIR) {
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
        if (state.getHardness(world, pos) < 0 && !player.isCreative) {
            return BuildResult.Unbreakable(pos, state)
        }

        return null
    }

    private fun SafeContext.checkPlaceResults(
        pos: BlockPos,
        target: TargetState,
        eye: Vec3d,
        reach: Double
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()

        if (target is TargetState.Air || !pos.blockState(world).isReplaceable) return acc

        val interact = TaskFlow.interact
        val rotation = TaskFlow.rotation

        Direction.entries.forEach { neighbor ->
            val hitPos = pos.offset(neighbor)
            val hitSide = neighbor.opposite

            val voxelShape = hitPos.blockState(world).getOutlineShape(world, hitPos)
            if (voxelShape.isEmpty) return@forEach

            val boxes = voxelShape.boundingBoxes.map { it.offset(hitPos) }
            val verify: HitResult.() -> Boolean = {
                blockResult?.blockPos == hitPos && blockResult?.side == hitSide
            }
            val validHits = mutableMapOf<Vec3d, HitResult>()
            val misses = mutableSetOf<Vec3d>()
            val reachSq = reach.pow(2)

            boxes.forEach { box ->
                val res = if (TaskFlow.interact.useRayCast) interact.resolution else 4
                val half = (target as? TargetState.State)
                    ?.blockState
                    ?.getOrEmpty(Properties.SLAB_TYPE)
                    ?.scanMode ?: VisibilityChecker.ScanMode.BOTH
                scanVisibleSurfaces(eye, box, setOf(hitSide), res, half) { side, vec ->
                    if (eye distSq vec > reachSq) {
                        misses.add(vec)
                        return@scanVisibleSurfaces
                    }

                    validHits[vec] = if (TaskFlow.interact.useRayCast) {
                        val cast = eye.rotationTo(vec)
                            .rayCast(reach, eye) ?: return@scanVisibleSurfaces
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

            if (validHits.isEmpty()) {
                if (misses.isNotEmpty()) {
                    acc.add(BuildResult.OutOfReach(pos, eye, misses))
                    return@forEach
                }

                acc.add(BuildResult.NotVisible(pos, hitPos, hitSide, eye.distanceTo(hitPos.vecOf(hitSide))))
                return@forEach
            }

            validHits.keys.optimum?.let { optimum ->
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
                    acc.add(PlaceResult.BlockedByPlayer(pos))
                    return@forEach
                }

                if (!target.matches(resultState, pos, world)) {
                    acc.add(PlaceResult.NoIntegrity(
                        pos, resultState, context, (target as? TargetState.State)?.blockState)
                    )
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
                    Hand.MAIN_HAND,
                    target,
                    shouldSneak,
                    false
                )

                val currentHandStack = player.getStackInHand(Hand.MAIN_HAND)
                if (target is TargetState.Stack && !target.itemStack.equal(currentHandStack)) {
                    acc.add(BuildResult.WrongStack(pos, placeContext, target.itemStack))
                    return@forEach
                }
                
                if (optimalStack.item != currentHandStack.item) {
                    acc.add(BuildResult.WrongItem(pos, placeContext, optimalStack.item))
                    return@forEach
                }

                acc.add(PlaceResult.Place(pos, placeContext))
            }
        }

        return acc
    }

    private fun SafeContext.checkBreakResults(
        pos: BlockPos,
        eye: Vec3d,
        reach: Double
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val state = pos.blockState(world)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (TaskFlow.build.breakWeakBlocks && state.block.hardness == 0f && !state.isAir) {
            acc.add(BuildResult.Ignored(pos))
            return acc
        }

        /* player is standing on top of the block */
        val pBox = player.boundingBox
        val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
        world.findSupportingBlockPos(player, aabb).orElse(null)?.let { support ->
            if (support != pos) return@let
            val belowSupport = support.down().blockState(world)
            if (belowSupport.isSolidSurface(world, support, player, Direction.UP)) return@let
            acc.add(BreakResult.PlayerOnTop(pos, state))
            return acc
        }

        /* liquid needs to be submerged first to be broken */
        if (!state.fluidState.isEmpty && state.isReplaceable) {
            val submerge = checkPlaceResults(pos, TargetState.Solid, eye, reach)
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
                val submerge = checkPlaceResults(pos.offset(it), TargetState.Solid, eye, reach)
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

        val interact = TaskFlow.interact
        val rotation = TaskFlow.rotation
        val currentRotation = RotationManager.currentRotation
        val currentCast = currentRotation.rayCast(reach, eye)

        val voxelShape = state.getOutlineShape(world, pos)
        voxelShape.getClosestPointTo(eye).ifPresent {
            // ToDo: Use closest point of shape of only visible faces
        }

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
                acc.add(BreakResult.Break(pos, breakContext))
                return acc
            }
        }

        val validHits = mutableMapOf<Vec3d, HitResult>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = reach.pow(2)

        boxes.forEach { box ->
            val res = if (TaskFlow.interact.useRayCast) interact.resolution else 2
            scanVisibleSurfaces(eye, box, emptySet(), res) { side, vec ->
                if (eye distSq vec > reachSq) {
                    misses.add(vec)
                    return@scanVisibleSurfaces
                }

                validHits[vec] = if (TaskFlow.interact.useRayCast) {
                    val cast = eye.rotationTo(vec)
                        .rayCast(reach, eye) ?: return@scanVisibleSurfaces
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

        if (validHits.isEmpty()) {
            // ToDo: If we can only mine exposed surfaces we need to add not visible result here
            acc.add(BuildResult.OutOfReach(pos, eye, misses))
            return acc
        }

        validHits.keys.optimum?.let { optimum ->
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
            if (!player.isCreative) findBestAvailableTool(state)?.let { bestTool ->
                Hand.entries.firstOrNull {
                    val stack = player.getStackInHand(it)
                    stack.item == bestTool
                }?.let { hand ->
                    breakContext.hand = hand
                    acc.add(BreakResult.Break(pos, breakContext))
                    return acc
                } ?: run {
                    acc.add(BuildResult.WrongItem(pos, breakContext, bestTool))
                    return acc
                }
            }

            acc.add(BreakResult.Break(pos, breakContext))
        }
        return acc
    }
}