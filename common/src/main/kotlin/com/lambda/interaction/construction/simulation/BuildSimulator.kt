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

package com.lambda.interaction.construction.simulation

import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.InventoryConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.processing.ProcessorRegistry.findProcessorForState
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotation.Rotation.Companion.rotation
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.interaction.request.rotation.visibilty.*
import com.lambda.interaction.request.rotation.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotation.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotation.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.vecOf
import com.lambda.util.Communication.warn
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.item.ItemUtils.findBestToolsForBreaking
import com.lambda.util.math.distSq
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.player.copyPlayer
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.OperatorBlock
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.RegistryKeys
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull
import kotlin.math.pow

object BuildSimulator {
    fun Blueprint.simulate(
        eye: Vec3d,
        interact: InteractionConfig = TaskFlowModule.interact,
        rotation: RotationConfig = TaskFlowModule.rotation,
        inventory: InventoryConfig = TaskFlowModule.inventory,
        build: BuildConfig = TaskFlowModule.build,
    ) = runSafe {
        structure.entries.flatMap { (pos, target) ->
            checkRequirements(pos, target, build)?.let {
                return@flatMap setOf(it)
            }
            checkPlaceResults(pos, target, eye, interact, rotation, inventory).let {
                if (it.isEmpty()) return@let
                return@flatMap it
            }
            checkBreakResults(pos, eye, interact, rotation, inventory, build).let {
                if (it.isEmpty()) return@let
                return@flatMap it
            }
            warn("Nothing matched $pos $target")
            emptySet()
        }.toSet()
    } ?: emptySet()

    private fun SafeContext.checkRequirements(
        pos: BlockPos,
        target: TargetState,
        build: BuildConfig
    ): BuildResult? {/* the chunk is not loaded */
        if (!world.isChunkLoaded(pos)) {
            return BuildResult.ChunkNotLoaded(pos)
        }

        val state = blockState(pos)

        /* block is already in the correct state */
        if (target.matches(state, pos, world)) {
            return BuildResult.Done(pos)
        }

        /* block should be ignored */
        if (state.block in build.breakSettings.ignoredBlocks && target.type == TargetState.Type.AIR) {
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
        interact: InteractionConfig,
        rotation: RotationConfig,
        inventory: InventoryConfig
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val targetPosState = blockState(pos)

        if (target.isAir() || !targetPosState.isReplaceable) return acc

        val preprocessing = target.findProcessorForState()

        preprocessing.sides.forEach { neighbor ->
            val hitPos = if (targetPosState.isAir || targetPosState.isLiquid) pos.offset(neighbor) else pos
            val hitSide = neighbor.opposite

            val voxelShape = blockState(hitPos).getOutlineShape(world, hitPos)
            if (voxelShape.isEmpty) return@forEach

            val boxes = voxelShape.boundingBoxes.map { it.offset(hitPos) }
            val verify: CheckedHit.() -> Boolean = {
                hit.blockResult?.blockPos == hitPos && hit.blockResult?.side == hitSide
            }

            val validHits = mutableListOf<CheckedHit>()
            val misses = mutableSetOf<Vec3d>()
            val reachSq = interact.interactReach.pow(2)

            boxes.forEach { box ->
                val sides = if (interact.checkSideVisibility) {
                    box.getVisibleSurfaces(eye).intersect(setOf(hitSide))
                } else {
                    Direction.entries.toSet()
                }

                scanSurfaces(box, sides, interact.resolution, preprocessing.surfaceScan) { _, vec ->
                    if (eye distSq vec > reachSq) {
                        misses.add(vec)
                        return@scanSurfaces
                    }

                    val newRotation = eye.rotationTo(vec)

                    val hit = if (interact.strictRayCast) {
                        newRotation.rayCast(interact.interactReach, eye)?.blockResult
                    } else {
                        val hitVec = newRotation.castBox(box, interact.interactReach, eye)
                        BlockHitResult(hitVec, hitSide, hitPos, false)
                    } ?: return@scanSurfaces

                    val checked = CheckedHit(hit, newRotation, interact.interactReach)
                    if (!checked.verify()) return@scanSurfaces

                    validHits.add(checked)
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

            interact.pointSelection.select(validHits)?.let { checkedHit ->
                val optimalStack = target.getStack(world, pos)

                // ToDo: For each hand and sneak or not?
                val fakePlayer = copyPlayer(player).apply {
                    setPos(eye.x, eye.y - standingEyeHeight, eye.z)
                    this.rotation = checkedHit.targetRotation
                }

                val checkedResult = checkedHit.hit

                val usageContext = ItemUsageContext(
                    fakePlayer,
                    Hand.MAIN_HAND,
                    checkedResult.blockResult,
                )
                val cachePos = CachedBlockPosition(
                    usageContext.world, usageContext.blockPos, false
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

                if (context.blockPos != pos) {
                    acc.add(PlaceResult.UnexpectedPosition(pos, context.blockPos))
                    return@forEach
                }

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
                    acc.add(PlaceResult.BlockedByEntity(pos))
                    return@forEach
                }

                if (!target.matches(resultState, pos, world)) {
                    acc.add(
                        PlaceResult.NoIntegrity(
                            pos, resultState, context, (target as? TargetState.State)?.blockState
                        )
                    )
                    return@forEach
                }

                val blockHit = checkedResult.blockResult ?: return@forEach
                val hitBlock = blockState(blockHit.blockPos).block
                val shouldSneak = hitBlock::class in BlockUtils.interactionBlocks

                val primeDirection =
                    (target as? TargetState.State)?.blockState?.getOrEmpty(Properties.HORIZONTAL_FACING)?.getOrNull()

                val placeContext = PlaceContext(
                    eye,
                    blockHit,
                    RotationRequest(lookAt(checkedHit.targetRotation, 0.001), rotation),
                    eye.distanceTo(blockHit.pos),
                    resultState,
                    blockState(blockHit.blockPos),
                    player.inventory.selectedSlot,
                    context.blockPos,
                    target,
                    shouldSneak,
                    false,
                    primeDirection
                )

                val currentHandStack = player.getStackInHand(Hand.MAIN_HAND)
                if (target is TargetState.Stack && !target.itemStack.equal(currentHandStack)) {
                    acc.add(BuildResult.WrongStack(pos, placeContext, target.itemStack, inventory))
                    return@forEach
                }

                if (optimalStack.item != currentHandStack.item) {
                    acc.add(BuildResult.WrongItemSelection(pos, placeContext, optimalStack.item.select(), currentHandStack, inventory))
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
        interact: InteractionConfig,
        rotation: RotationConfig,
        inventory: InventoryConfig,
        build: BuildConfig
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val state = blockState(pos)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (build.breakSettings.breakWeakBlocks && state.block.hardness == 0f && !state.isAir) {
            acc.add(BuildResult.Ignored(pos))
            return acc
        }

        /* player is standing on top of the block */
        val pBox = player.boundingBox
        val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
        world.findSupportingBlockPos(player, aabb).orElse(null)?.let { support ->
            if (support != pos) return@let
            val belowSupport = blockState(support.down())
            if (belowSupport.isSolidSurface(world, support, player, Direction.UP)) return@let
            acc.add(BreakResult.PlayerOnTop(pos, state))
            return acc
        }

        /* liquid needs to be submerged first to be broken */
        if (!state.fluidState.isEmpty && state.isReplaceable) {
            val submerge = checkPlaceResults(pos, TargetState.Solid, eye, interact, rotation, inventory)
            acc.add(BreakResult.Submerge(pos, state, submerge))
            acc.addAll(submerge)
            return acc
        }

        val adjacentLiquids = Direction.entries.filter {
            it != Direction.DOWN && !blockState(pos.offset(it)).fluidState.isEmpty
        }.map { pos.offset(it) }

        /* block has liquids next to it that will leak when broken */
        if (adjacentLiquids.isNotEmpty()) {
            acc.add(BreakResult.BlockedByLiquid(pos, state))
            adjacentLiquids.forEach { liquidPos ->
                val submerge = if (blockState(liquidPos).isReplaceable) {
                    checkPlaceResults(liquidPos, TargetState.Solid, eye, interact, rotation, inventory)
                } else {
                    checkBreakResults(liquidPos, eye, interact, rotation, inventory, build)
                }
                acc.addAll(submerge)
            }
            return acc
        }

        val currentRotation = RotationManager.currentRotation
        val currentCast = currentRotation.rayCast(interact.interactReach, eye)

        val voxelShape = state.getOutlineShape(world, pos)
        voxelShape.getClosestPointTo(eye).ifPresent {
            // ToDo: Use closest point of shape of only visible faces
        }

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }
        val verify: CheckedHit.() -> Boolean = {
            hit.blockResult?.blockPos == pos
        }
        val targetState = if (!state.fluidState.isEmpty) {
            TargetState.State(state.fluidState.blockState)
        } else {
            TargetState.Air
        }

        /* the player is buried inside the block */
        if (boxes.any { it.contains(eye) }) {
            currentCast?.blockResult?.let { blockHit ->
                val rotationRequest = RotationRequest(
                    lookAtBlock(pos, config = interact), rotation
                )
                val breakContext = BreakContext(
                    eye,
                    blockHit,
                    rotationRequest,
                    state,
                    targetState,
                    player.inventory.selectedSlot,
                    instantBreakable(state, pos),
                    build,
                    inventory
                )
                acc.add(BreakResult.Break(pos, breakContext))
                return acc
            }
        }

        val validHits = mutableListOf<CheckedHit>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = interact.interactReach.pow(2)

        boxes.forEach { box ->
            // ToDo: Rewrite Rotation request system to allow support for all sim features and use the rotation finder
            scanSurfaces(box, Direction.entries.toSet(), interact.resolution) { side, vec ->
                if (eye distSq vec > reachSq) {
                    misses.add(vec)
                    return@scanSurfaces
                }

                val newRotation = eye.rotationTo(vec)

                val hit = if (interact.strictRayCast) {
                    newRotation.rayCast(interact.interactReach, eye)?.blockResult
                } else {
                    val hitVec = newRotation.castBox(box, interact.interactReach, eye)
                    BlockHitResult(hitVec, side, pos, false)
                } ?: return@scanSurfaces

                val checked = CheckedHit(hit, newRotation, interact.interactReach)
                if (!checked.verify()) return@scanSurfaces

                validHits.add(checked)
            }
        }

        if (validHits.isEmpty()) {
            // ToDo: If we can only mine exposed surfaces we need to add not visible result here
            acc.add(BuildResult.OutOfReach(pos, eye, misses))
            return acc
        }

        val bestHit = interact.pointSelection.select(validHits) ?: return acc
        val blockHit = bestHit.hit.blockResult ?: return acc
        val target = lookAt(bestHit.targetRotation, 0.001)
        val request = RotationRequest(target, rotation)
        val instant = instantBreakable(state, pos)

        val breakContext = BreakContext(
            eye, blockHit, request, state, targetState, player.inventory.selectedSlot, instant, build, inventory
        )

        if (player.isCreative) {
            acc.add(BreakResult.Break(pos, breakContext))
            return acc
        }

        val bestTools = findBestToolsForBreaking(state, inventory.allowedTools)

        /* there is no good tool for the job */
        if (bestTools.isEmpty()) {
            /* The current selected item cant mine the block */
            Hand.entries.forEach {
                val stack = player.getStackInHand(it)
                if (stack.isEmpty) return@forEach
                if (stack.item.canMine(state, world, pos, player)) return@forEach
                acc.add(BreakResult.ItemCantMine(pos, state, stack.item, inventory))
                return acc
            }
            // ToDo: Switch to non destroyable item
            acc.add(BreakResult.Break(pos, breakContext))
            return acc
        }

        val toolSelection = if (build.breakSettings.forceSilkTouch) {
            selectStack { isOneOfItems(bestTools) and hasEnchantment(Enchantments.SILK_TOUCH) }
        } else if (build.breakSettings.forceFortunePickaxe) {
            selectStack { isOneOfItems(bestTools) and hasEnchantment(Enchantments.FORTUNE, build.breakSettings.minFortuneLevel) }
        } else {
            bestTools.select()
        }
        val silentSwapSelection = selectContainer {
            matches(toolSelection) and ofAnyType(MaterialContainer.Rank.HOTBAR)
        }
	    val fullSelection = selectContainer {
			matches(toolSelection) and matches(inventory.containerSelection)
	    }

        val swapCandidates = toolSelection.containerWithMaterial(inventory, silentSwapSelection)
        if (swapCandidates.isEmpty()) {
            acc.add(BuildResult.WrongItemSelection(pos, breakContext, toolSelection, player.mainHandStack, inventory))
            return acc
        }

        val matchingStacks = swapCandidates.associateWith { it.matchingStacks(toolSelection) }
        val (container, toolPair) = matchingStacks.mapValues { (_, stacks) ->
	        stacks.associateWith { state.calcItemBlockBreakingDelta(player, world, pos, it) }
                .maxByOrNull { it.value }
                ?.toPair()
        }.entries.maxByOrNull { it.value?.second ?: 0f }?.toPair() ?: return acc

        if (toolPair == null) return acc

        breakContext.hotbarIndex = player.hotbar.indexOf(toolPair.first)
	    acc.add(BreakResult.Break(pos, breakContext))
        return acc
    }
}
