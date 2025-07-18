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
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.processing.PreProcessingInfo
import com.lambda.interaction.construction.processing.ProcessorRegistry.getProcessingInfo
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.InteractResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.placing.PlaceConfig
import com.lambda.interaction.request.rotating.Rotation.Companion.rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.RotationConfig
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.PlaceDirection
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.interaction.request.rotating.visibilty.lookAtBlock
import com.lambda.interaction.request.rotating.visibilty.lookInDirection
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.BlockUtils.vecOf
import com.lambda.util.Communication.warn
import com.lambda.util.math.distSq
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.player.copyPlayer
import com.lambda.util.player.gamemode
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.block.OperatorBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.Waterloggable
import net.minecraft.block.enums.SlabType
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.enchantment.Enchantments
import net.minecraft.fluid.FlowableFluid
import net.minecraft.fluid.LavaFluid
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.registry.RegistryKeys
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.pow

object BuildSimulator {
    fun Blueprint.simulate(
        eye: Vec3d,
        interactionConfig: InteractionConfig = TaskFlowModule.interaction,
        rotation: RotationConfig = TaskFlowModule.rotation,
        inventory: InventoryConfig = TaskFlowModule.inventory,
        build: BuildConfig = TaskFlowModule.build,
    ) = runSafe {
        structure.entries.flatMap { (pos, target) ->
            val preProcessing = target.getProcessingInfo(pos) ?: return@flatMap emptySet()
            checkRequirements(pos, target, build).let {
                if (it.isEmpty()) return@let
                return@flatMap it
            }
            checkPostProcessResults(pos, eye, preProcessing, target, interactionConfig, build.placing, rotation, inventory).let {
                if (it.isEmpty()) return@let
                return@flatMap it
            }
            checkPlaceResults(pos, eye, preProcessing, target, build.placing, interactionConfig, rotation, inventory).let {
                if (it.isEmpty()) return@let
                return@flatMap it
            }
            checkBreakResults(pos, eye, preProcessing, build.breaking, interactionConfig, rotation, inventory, build).let {
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
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()

        /* the chunk is not loaded */
        if (!world.isChunkLoaded(pos)) {
            acc.add(BuildResult.ChunkNotLoaded(pos))
            return acc
        }

        val state = blockState(pos)

        /* block is already in the correct state */
        if (target.matches(state, pos, world)) {
            acc.add(BuildResult.Done(pos))
            return acc
        }

        /* block should be ignored */
        if (state.block in build.breaking.ignoredBlocks && target.type == TargetState.Type.AIR) {
            acc.add(BuildResult.Ignored(pos))
            return acc
        }

        /* the player is in the wrong game mode to alter the block state */
        if (player.isBlockBreakingRestricted(world, pos, gamemode)) {
            acc.add(BuildResult.Restricted(pos))
            return acc
        }

        /* the player has no permissions to alter the block state */
        if (state.block is OperatorBlock && !player.isCreativeLevelTwoOp) {
            acc.add(BuildResult.NoPermission(pos, state))
            return acc
        }

        /* block is outside the world so it cant be altered */
        if (!world.worldBorder.contains(pos) || world.isOutOfHeightLimit(pos)) {
            acc.add(BuildResult.OutOfWorld(pos))
            return acc
        }

        /* block is unbreakable, so it cant be broken or replaced */
        if (state.getHardness(world, pos) < 0 && !gamemode.isCreative) {
            acc.add(BuildResult.Unbreakable(pos, state))
            return acc
        }

        return acc
    }

    private fun SafeContext.checkPostProcessResults(
        pos: BlockPos,
        eye: Vec3d,
        preProcessing: PreProcessingInfo,
        targetState: TargetState,
        interactionConfig: InteractionConfig,
        place: PlaceConfig,
        rotation: RotationConfig,
        inventory: InventoryConfig
    ): Set<BuildResult> {
        if (targetState !is TargetState.State) return emptySet()

        val acc = mutableSetOf<BuildResult>()

        val state = blockState(pos)
        if (!targetState.matches(state, pos, world, preProcessing.ignore))
            return acc

        val interactBlock: (BlockState, Set<Direction>?, Item?, Boolean) -> Unit = interactBlock@ { expectedState, sides, item, placing ->
            val boxes = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
            val validHits = mutableListOf<CheckedHit>()
            val blockedHits = mutableSetOf<Vec3d>()
            val misses = mutableSetOf<Vec3d>()
            val airPlace = placing && place.airPlace.isEnabled()

            boxes.forEach { box ->
                val refinedSides = if (interactionConfig.checkSideVisibility) {
                    box.getVisibleSurfaces(eye).let { visibleSides ->
                        sides?.let { specific ->
                            visibleSides.intersect(specific)
                        } ?: visibleSides.toSet()
                    }
                } else sides ?: Direction.entries.toSet()

                scanSurfaces(box, refinedSides, interactionConfig.resolution, preProcessing.surfaceScan) { hitSide, vec ->
                    val distSquared = eye distSq vec
                    if (distSquared > interactionConfig.interactReach.pow(2)) {
                        misses.add(vec)
                        return@scanSurfaces
                    }

                    val newRotation = eye.rotationTo(vec)

                    val hit = if (interactionConfig.strictRayCast) {
                        val rayCast = newRotation.rayCast(interactionConfig.interactReach, eye)
                        when {
                            rayCast != null && (!airPlace || eye distSq rayCast.pos <= distSquared) ->
                                rayCast.blockResult

                            airPlace -> {
                                val hitVec = newRotation.castBox(box, interactionConfig.interactReach, eye)
                                BlockHitResult(hitVec, hitSide, pos, false)
                            }

                            else -> null
                        }
                    } else {
                        val hitVec = newRotation.castBox(box, interactionConfig.interactReach, eye)
                        BlockHitResult(hitVec, hitSide, pos, false)
                    } ?: return@scanSurfaces

                    val checked = CheckedHit(hit, newRotation, interactionConfig.interactReach)
                    if (hit.blockResult?.blockPos != pos) {
                        blockedHits.add(vec)
                        return@scanSurfaces
                    }

                    validHits.add(checked)
                }
            }

            if (validHits.isEmpty()) {
                if (misses.isNotEmpty()) {
                    acc.add(BuildResult.OutOfReach(pos, eye, misses))
                } else {
                    //ToDo: Must clean up surface scan usage / renders. Added temporary direction until changes are made
                    acc.add(BuildResult.NotVisible(pos, pos, Direction.UP, eye.distanceTo(pos.vecOf(Direction.UP))))
                }
                return@interactBlock
            }

            interactionConfig.pointSelection.select(validHits)?.let { checkedHit ->
                val checkedResult = checkedHit.hit
                val rotationTarget = lookAt(checkedHit.targetRotation, 0.001)
                val context = InteractionContext(
                    checkedResult.blockResult ?: return@interactBlock,
                    RotationRequest(rotationTarget, rotation),
                    player.inventory.selectedSlot,
                    state,
                    expectedState
                )

                val stackSelection = (item ?: player.mainHandStack.item).select()
                val hotbarCandidates = selectContainer {
                    matches(stackSelection) and ofAnyType(MaterialContainer.Rank.HOTBAR)
                }.let { predicate ->
                    stackSelection.containerWithMaterial(inventory, predicate)
                }

                if (hotbarCandidates.isEmpty()) {
                    acc.add(BuildResult.WrongItemSelection(pos, context, stackSelection, player.mainHandStack, inventory))
                    return@interactBlock
                } else {
                    context.hotbarIndex = player.hotbar.indexOf(hotbarCandidates.first().matchingStacks(stackSelection).first())
                }

                acc.add(InteractResult.Interact(pos, context))
            }
        }

        val mismatchedProperties = state.properties.filter { state.get(it) != targetState.blockState.get(it) }
        mismatchedProperties.forEach { property ->
            when (property) {
                Properties.EYE -> {
                    if (state.get(Properties.EYE)) return@forEach
                    val expectedState = state.with(Properties.EYE, true)
                    interactBlock(expectedState, null, null, false)
                }
                Properties.INVERTED -> {
                    val expectedState = state.with(Properties.INVERTED, !state.get(Properties.INVERTED))
                    interactBlock(expectedState, null, null, false)
                }
                Properties.DELAY -> {
                    val expectedState = state.with(Properties.DELAY, state.cycle(Properties.DELAY).get(Properties.DELAY))
                    interactBlock(expectedState, null, null, false)
                }
                Properties.COMPARATOR_MODE -> {
                    val expectedState = state.with(Properties.COMPARATOR_MODE, state.cycle(Properties.COMPARATOR_MODE).get(Properties.COMPARATOR_MODE))
                    interactBlock(expectedState, null, null, false)
                }
                Properties.OPEN -> {
                    val expectedState = state.with(Properties.OPEN, !state.get(Properties.OPEN))
                    interactBlock(expectedState, null, null, false)
                }
                Properties.SLAB_TYPE -> {
                    if (targetState.blockState.get(Properties.SLAB_TYPE) != SlabType.DOUBLE) return@forEach
                    checkPlaceResults(pos, eye, preProcessing, targetState, place, interactionConfig, rotation, inventory).let { placeResults ->
                        acc.addAll(placeResults)
                    }
                }
            }
        }

        if (acc.isEmpty()) {
            acc.add(BuildResult.Done(pos))
        }

        return acc
    }

    private fun SafeContext.checkPlaceResults(
        pos: BlockPos,
        eye: Vec3d,
        preProcessing: PreProcessingInfo,
        targetState: TargetState,
        place: PlaceConfig,
        interactionConfig: InteractionConfig,
        rotation: RotationConfig,
        inventory: InventoryConfig
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val currentState = blockState(pos)

        val statePromoting = currentState.block is SlabBlock && targetState.matches(currentState, pos, world, preProcessing.ignore)
        if (targetState.isEmpty() || (!currentState.isReplaceable && !statePromoting)) return acc

        preProcessing.sides.forEach { neighbor ->
            val hitPos = if (!place.airPlace.isEnabled() && (currentState.isEmpty || statePromoting))
                pos.offset(neighbor)
            else pos
            val hitSide = neighbor.opposite

            val voxelShape = blockState(hitPos).getOutlineShape(world, hitPos).let { outlineShape ->
                if (!outlineShape.isEmpty || !place.airPlace.isEnabled()) outlineShape
                else VoxelShapes.fullCube()
            }
            if (voxelShape.isEmpty) return@forEach

            val boxes = voxelShape.boundingBoxes.map { it.offset(hitPos) }
            val verify: CheckedHit.() -> Boolean = {
                hit.blockResult?.blockPos == hitPos && hit.blockResult?.side == hitSide
            }

            val validHits = mutableListOf<CheckedHit>()
            val misses = mutableSetOf<Vec3d>()
            val reachSq = interactionConfig.interactReach.pow(2)

            boxes.forEach { box ->
                val sides = if (interactionConfig.checkSideVisibility) {
                    box.getVisibleSurfaces(eye).intersect(setOf(hitSide))
                } else setOf(hitSide)

                scanSurfaces(box, sides, interactionConfig.resolution, preProcessing.surfaceScan) { _, vec ->
                    val distSquared = eye distSq vec
                    if (distSquared > reachSq) {
                        misses.add(vec)
                        return@scanSurfaces
                    }

                    val newRotation = eye.rotationTo(vec)

                    val hit = if (interactionConfig.strictRayCast) {
                        val rayCast = newRotation.rayCast(interactionConfig.interactReach, eye)
                        when {
                            rayCast != null && (!place.airPlace.isEnabled() || eye distSq rayCast.pos <= distSquared) ->
                                rayCast.blockResult

                            place.airPlace.isEnabled() -> {
                                val hitVec = newRotation.castBox(box, interactionConfig.interactReach, eye)
                                BlockHitResult(hitVec, hitSide, hitPos, false)
                            }

                            else -> null
                        }
                    } else {
                        val hitVec = newRotation.castBox(box, interactionConfig.interactReach, eye)
                        BlockHitResult(hitVec, hitSide, hitPos, false)
                    } ?: return@scanSurfaces

                    val checked = CheckedHit(hit, newRotation, interactionConfig.interactReach)
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

            interactionConfig.pointSelection.select(validHits)?.let { checkedHit ->
                val optimalStack = targetState.getStack(world, pos, inventory)

                // ToDo: For each hand and sneak or not?
                val fakePlayer = copyPlayer(player).apply {
                    this.rotation = RotationManager.serverRotation
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

                if (!context.canPlace() && !statePromoting) {
                    acc.add(PlaceResult.CantReplace(pos, context))
                    return@forEach
                }

                val blockItem = optimalStack.item as? BlockItem ?: run {
                    acc.add(PlaceResult.NotItemBlock(pos, optimalStack))
                    return@forEach
                }

                context = blockItem.getPlacementContext(context)
                    ?: run {
                        acc.add(PlaceResult.ScaffoldExceeded(pos, context))
                        return@forEach
                    }

                lateinit var resultState: BlockState
                var rot = fakePlayer.rotation

                val simulatePlaceState = placeState@ {
                    resultState = blockItem.getPlacementState(context)
                        ?: return@placeState PlaceResult.BlockedByEntity(pos)

                    return@placeState if (!targetState.matches(resultState, pos, world, preProcessing.ignore))
                        PlaceResult.NoIntegrity(pos, resultState, context, (targetState as? TargetState.State)?.blockState)
                    else null
                }

                val currentDirIsValid = simulatePlaceState()?.let { basePlaceResult ->
                    if (!place.rotateForPlace) {
                        acc.add(basePlaceResult)
                        return@forEach
                    }
                    false
                } ?: true

                run rotate@ {
                    if (!place.axisRotate) {
                        fakePlayer.rotation = checkedHit.targetRotation
                        simulatePlaceState()?.let { rotatedPlaceResult ->
                            acc.add(rotatedPlaceResult)
                            return@forEach
                        }
                        rot = fakePlayer.rotation
                        return@rotate
                    }

                    fakePlayer.rotation = player.rotation
                    if (simulatePlaceState() == null) {
                        rot = fakePlayer.rotation
                        return@rotate
                    }

                    PlaceDirection.entries.asReversed().forEachIndexed direction@ { index, direction ->
                        fakePlayer.rotation = direction.rotation
                        when (val placeResult = simulatePlaceState()) {
                            is PlaceResult.BlockedByEntity -> {
                                acc.add(placeResult)
                                return@forEach
                            }

                            is PlaceResult.NoIntegrity -> {
                                if (index != PlaceDirection.entries.lastIndex) return@direction
                                acc.add(placeResult)
                                return@forEach
                            }

                            else -> {
                                rot = fakePlayer.rotation
                                return@rotate
                            }
                        }
                    }
                }

                val blockHit = checkedResult.blockResult ?: return@forEach
                val hitBlock = blockState(blockHit.blockPos).block
                val shouldSneak = hitBlock::class in BlockUtils.interactionBlocks

                val rotationRequest = if (place.axisRotate) {
                    lookInDirection(PlaceDirection.fromRotation(rot))
                } else lookAt(rot, 0.001)

                val placeContext = PlaceContext(
                    blockHit,
                    RotationRequest(rotationRequest, rotation),
                    player.inventory.selectedSlot,
                    context.blockPos,
                    blockState(context.blockPos),
                    resultState,
                    shouldSneak,
                    false,
                    currentDirIsValid
                )

                val selection = optimalStack.item.select()
                val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.HOTBAR) }
                val container = selection.containerWithMaterial(inventory, containerSelection).firstOrNull() ?: run {
                    acc.add(BuildResult.WrongItemSelection(pos, placeContext, optimalStack.item.select(), player.mainHandStack, inventory))
                    return acc
                }
                val stack = selection.filterStacks(container.stacks).run {
                    firstOrNull { player.inventory.getSlotWithStack(it) == player.inventory.selectedSlot }
                        ?: first()
                }

                placeContext.hotbarIndex = player.inventory.getSlotWithStack(stack)

                acc.add(PlaceResult.Place(pos, placeContext))
            }
        }

        return acc
    }

    private fun SafeContext.checkBreakResults(
        pos: BlockPos,
        eye: Vec3d,
        preProcessing: PreProcessingInfo,
        breaking: BreakConfig,
        interactionConfig: InteractionConfig,
        rotation: RotationConfig,
        inventory: InventoryConfig,
        build: BuildConfig
    ): Set<BuildResult> {
        val acc = mutableSetOf<BuildResult>()
        val state = blockState(pos)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (!breaking.breakWeakBlocks && state.block.hardness == 0f && state.isNotEmpty) {
            acc.add(BuildResult.Ignored(pos))
            return acc
        }

        /* player is standing on top of the block */
        val pBox = player.boundingBox
        val aabb = pBox.withMinY(pBox.minY - 1.0E-6)
        world.findSupportingBlockPos(player, aabb).orElse(null)?.let { support ->
            if (support != pos) return@let
            val belowSupport = blockState(support.down())
            if (belowSupport.isSolidSurface(world, support, player, Direction.UP)) return@let
            acc.add(BreakResult.PlayerOnTop(pos, state))
            return acc
        }

        /* liquid needs to be submerged first to be broken */
        if (!state.fluidState.isEmpty && state.isReplaceable) {
            val submerge = checkPlaceResults(pos, eye, preProcessing, TargetState.Solid, build.placing, interactionConfig, rotation, inventory)
            acc.add(BreakResult.Submerge(pos, state, submerge))
            acc.addAll(submerge)
            return acc
        }

        if (breaking.avoidLiquids) {
            val affectedBlocks = hashSetOf(pos)
            val checkQueue = hashSetOf(pos)

            while (checkQueue.isNotEmpty()) {
                val checkPos = checkQueue.first()
                checkQueue.remove(checkPos)
                for (offset in Direction.entries) {
                    val adjacentPos = checkPos.offset(offset)

                    if (blockState(adjacentPos).block !is FallingBlock) continue
                    if (adjacentPos in affectedBlocks) continue

                    if (offset == Direction.UP || FallingBlock.canFallThrough(blockState(adjacentPos.down()))) {
                        checkQueue.add(adjacentPos)
                        affectedBlocks.add(adjacentPos)
                    }
                }
            }

            val affectedFluids = affectedBlocks.fold(hashMapOf<BlockPos, BlockState>()) { accumulator, affectedPos ->
                Direction.entries.forEach { offset ->
                    if (offset == Direction.DOWN) return@forEach

                    val offsetPos = affectedPos.offset(offset)
                    val offsetState = blockState(offsetPos)
                    val fluidState = offsetState.fluidState
                    val fluid = fluidState.fluid

                    if (fluidState.isEmpty || fluid !is FlowableFluid) return@forEach

                    if (offset == Direction.UP) {
                        accumulator.put(offsetPos, offsetState)
                        return@fold accumulator
                    }

                    if (offsetState.block is Waterloggable && !fluidState.isEmpty) {
                        accumulator.put(offsetPos, offsetState)
                        return@fold accumulator
                    }

                    val levelDecreasePerBlock =
                        when (fluid) {
                            is WaterFluid -> fluid.getLevelDecreasePerBlock(world)
                            is LavaFluid -> fluid.getLevelDecreasePerBlock(world)
                            else -> 0
                        }

                    if (fluidState.level - levelDecreasePerBlock > 0) {
                        accumulator[offsetPos] = offsetState
                        return@fold accumulator
                    }
                }

                return@fold accumulator
            }

            /* block has liquids next to it that will leak when broken */
            if (affectedFluids.isNotEmpty()) {
                acc.add(BreakResult.BlockedByFluid(pos, state))
                affectedFluids.entries.forEach { fluid  ->
                    val submerge = if (fluid.value.isReplaceable) {
                        checkPlaceResults(fluid.key, eye, preProcessing, TargetState.Solid, build.placing, interactionConfig, rotation, inventory)
                    } else {
                        checkBreakResults(fluid.key, eye, preProcessing, breaking, interactionConfig, rotation, inventory, build)
                    }
                    acc.addAll(submerge)
                }
                return acc
            }
        }

        val currentRotation = RotationManager.activeRotation
        val currentCast = currentRotation.rayCast(interactionConfig.interactReach, eye)

        val voxelShape = state.getOutlineShape(world, pos)
        voxelShape.getClosestPointTo(eye).ifPresent {
            // ToDo: Use closest point of shape of only visible faces
        }

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }
        val verify: CheckedHit.() -> Boolean = {
            hit.blockResult?.blockPos == pos
        }

        /* the player is buried inside the block */
        if (boxes.any { it.contains(eye) }) {
            currentCast?.blockResult?.let { blockHit ->
                val rotationRequest = RotationRequest(
                    lookAtBlock(pos, config = interactionConfig), rotation
                )
                val breakContext = BreakContext(
                    blockHit,
                    rotationRequest,
                    player.inventory.selectedSlot,
                    state,
                    instantBreakable(state, pos, breaking.breakThreshold)
                )
                acc.add(BreakResult.Break(pos, breakContext))
                return acc
            }
        }

        val validHits = mutableListOf<CheckedHit>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = interactionConfig.interactReach.pow(2)

        boxes.forEach { box ->
            // ToDo: Rewrite Rotation request system to allow support for all sim features and use the rotation finder
            scanSurfaces(box, Direction.entries.toSet(), interactionConfig.resolution) { side, vec ->
                if (eye distSq vec > reachSq) {
                    misses.add(vec)
                    return@scanSurfaces
                }

                val newRotation = eye.rotationTo(vec)

                val hit = if (interactionConfig.strictRayCast) {
                    newRotation.rayCast(interactionConfig.interactReach, eye)?.blockResult
                } else {
                    val hitVec = newRotation.castBox(box, interactionConfig.interactReach, eye)
                    BlockHitResult(hitVec, side, pos, false)
                } ?: return@scanSurfaces

                val checked = CheckedHit(hit, newRotation, interactionConfig.interactReach)
                if (!checked.verify()) return@scanSurfaces

                validHits.add(checked)
            }
        }

        if (validHits.isEmpty()) {
            // ToDo: If we can only mine exposed surfaces we need to add not visible result here
            acc.add(BuildResult.OutOfReach(pos, eye, misses))
            return acc
        }

        val bestHit = interactionConfig.pointSelection.select(validHits) ?: return acc
        val blockHit = bestHit.hit.blockResult ?: return acc
        val target = lookAt(bestHit.targetRotation, 0.001)
        val rotationRequest = RotationRequest(target, rotation)
        val instant = instantBreakable(state, pos, breaking.breakThreshold)

        val breakContext = BreakContext(blockHit, rotationRequest, player.inventory.selectedSlot, state, instant)

        if (gamemode.isCreative) {
            acc.add(BreakResult.Break(pos, breakContext))
            return acc
        }

        val stackSelection = selectStack(
            block = {
                run {
                    if (breaking.suitableToolsOnly) isSuitableForBreaking(state)
                    else StackSelection.EVERYTHING
                } and if (breaking.forceSilkTouch) {
                    hasEnchantment(Enchantments.SILK_TOUCH)
                } else if (breaking.forceFortunePickaxe) {
                    hasEnchantment(Enchantments.FORTUNE, breaking.minFortuneLevel)
                } else StackSelection.EVERYTHING
            },
            sorter = compareByDescending<ItemStack> {
                it.canDestroy(world.registryManager.get(RegistryKeys.BLOCK), CachedBlockPosition(world, pos, false))
            }.thenByDescending {
                state.calcItemBlockBreakingDelta(player, world, pos, it)
            }
        )

        val silentSwapSelection = selectContainer {
            ofAnyType(MaterialContainer.Rank.HOTBAR)
        }

        val swapCandidates = stackSelection.containerWithMaterial(inventory, silentSwapSelection)
        if (swapCandidates.isEmpty()) {
            acc.add(BuildResult.WrongItemSelection(pos, breakContext, stackSelection, player.mainHandStack, inventory))
            return acc
        }

        val matchingStacks = swapCandidates.associateWith { it.matchingStacks(stackSelection) }
        val (_, toolPair) = matchingStacks.mapValues { (_, stacks) ->
	        stacks.associateWith { state.calcItemBlockBreakingDelta(player, world, pos, it) }
                .maxByOrNull { it.value }
                ?.toPair()
        }.entries.maxByOrNull { it.value?.second ?: 0f }?.toPair() ?: return acc

        if (toolPair == null) return acc

        breakContext.hotbarIndex = player.hotbar.indexOf(toolPair.first)
        breakContext.instantBreak = instantBreakable(state, pos, toolPair.first, breaking.breakThreshold)
	    acc.add(BreakResult.Break(pos, breakContext))
        return acc
    }
}