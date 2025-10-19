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

package com.lambda.interaction.construction.simulation.checks

import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.PlaceResult
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.Rotation.Companion.rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.PlaceDirection
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.interaction.request.rotating.visibilty.lookInDirection
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.hasFluid
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.math.distSq
import com.lambda.util.math.vec3d
import com.lambda.util.player.copyPlayer
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemUsageContext
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.pow

object PlaceChecks : SimChecker<PlaceResult>(), Dependable {
    override fun SimInfo.asDependant(buildResult: BuildResult) =
        PlaceResult.Dependency(pos, buildResult)

    context(automatedSafeContext: AutomatedSafeContext, dependable: Dependable?)
    fun SimInfo.checkPlacements(
        pos: BlockPos = this.pos,
        state: BlockState = this.state,
        targetState: TargetState = this.targetState
    ): Unit = with(automatedSafeContext) {
        checkDependant(dependable)

        val statePromoting = state.block is SlabBlock &&
                targetState.matches(state, pos, preProcessing.ignore)
        // If the target state is air then the only possible blocks it could place are to remove liquids so we use the Solid TargetState
        val nextTargetState = if (targetState is TargetState.Air) {
            if (state.hasFluid) TargetState.Solid
            else return
        } else if (targetState.isEmpty()) {
            // Otherwise if the target state is empty, there's no situation where placement would be required so we can return
            return
        } else targetState
        // For example, slabs count as state promoting because you are placing another block to promote the current state to the target state
        if (!state.isReplaceable && !statePromoting) return

        preProcessing.sides.forEach { neighbor ->
            val hitPos = if (!placeConfig.airPlace.isEnabled && (state.isEmpty || statePromoting))
                pos.offset(neighbor) else pos
            val hitSide = neighbor.opposite
            if (!world.worldBorder.contains(hitPos)) return@forEach

            val voxelShape = blockState(hitPos).getOutlineShape(world, hitPos).let { outlineShape ->
                if (!outlineShape.isEmpty || !placeConfig.airPlace.isEnabled) outlineShape
                else VoxelShapes.fullCube()
            }
            if (voxelShape.isEmpty) return@forEach

            val boxes = voxelShape.boundingBoxes.map { it.offset(hitPos) }
            val verify: CheckedHit.() -> Boolean = {
                hit.blockResult?.blockPos == hitPos && hit.blockResult?.side == hitSide
            }

            val validHits = mutableListOf<CheckedHit>()
            val misses = mutableSetOf<Vec3d>()
            val reachSq = buildConfig.interactReach.pow(2)

            boxes.forEach { box ->
                val sides = if (buildConfig.checkSideVisibility) {
                    box.getVisibleSurfaces(eye).intersect(setOf(hitSide))
                } else setOf(hitSide)

                scanSurfaces(box, sides, buildConfig.resolution, preProcessing.surfaceScan) { _, vec ->
                    val distSquared = eye distSq vec
                    if (distSquared > reachSq) {
                        misses.add(vec)
                        return@scanSurfaces
                    }

                    val newRotation = eye.rotationTo(vec)

                    val hit = if (buildConfig.strictRayCast) {
                        val rayCast = newRotation.rayCast(buildConfig.interactReach, eye)
                        when {
                            rayCast != null && (!placeConfig.airPlace.isEnabled || eye distSq rayCast.pos <= distSquared) ->
                                rayCast.blockResult

                            placeConfig.airPlace.isEnabled -> {
                                val hitVec = newRotation.castBox(box, buildConfig.interactReach, eye)
                                BlockHitResult(hitVec, hitSide, hitPos, false)
                            }

                            else -> null
                        }
                    } else {
                        val hitVec = newRotation.castBox(box, buildConfig.interactReach, eye)
                        BlockHitResult(hitVec, hitSide, hitPos, false)
                    } ?: return@scanSurfaces

                    val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)
                    if (!checked.verify()) return@scanSurfaces

                    validHits.add(checked)
                }
            }

            if (validHits.isEmpty()) {
                if (misses.isNotEmpty()) {
                    result(GenericResult.OutOfReach(pos, eye, misses))
                    return@forEach
                }

                result(GenericResult.NotVisible(pos, hitPos, hitSide, eye.distanceTo(hitPos.offset(hitSide).vec3d)))
                return@forEach
            }

            buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
                val optimalStack = nextTargetState.getStack(pos)

                // ToDo: For each hand and sneak or not?
                val fakePlayer = copyPlayer(player).apply {
                    this.rotation = RotationManager.serverRotation
                }

                val checkedResult = checkedHit.hit

                // ToDo: Override the stack used for this to account for blocks where replaceability is dependent on the held item
                val usageContext = ItemUsageContext(
                    fakePlayer,
                    Hand.MAIN_HAND,
                    checkedResult.blockResult,
                )
                val cachePos = CachedBlockPosition(
                    usageContext.world, usageContext.blockPos, false
                )
                val canBePlacedOn = optimalStack.canPlaceOn(cachePos)
                if (!player.abilities.allowModifyWorld && !canBePlacedOn) {
                    result(PlaceResult.IllegalUsage(pos))
                    return@forEach
                }

                var context = ItemPlacementContext(usageContext)

                if (context.blockPos != pos) {
                    result(PlaceResult.UnexpectedPosition(pos, context.blockPos))
                    return@forEach
                }

                if (!optimalStack.item.isEnabled(world.enabledFeatures)) {
                    result(PlaceResult.BlockFeatureDisabled(pos, optimalStack))
                    return@forEach
                }

                if (!context.canPlace() && !statePromoting) {
                    result(PlaceResult.CantReplace(pos, context))
                    return@forEach
                }

                val blockItem = optimalStack.item as? BlockItem ?: run {
                    result(PlaceResult.NotItemBlock(pos, optimalStack))
                    return@forEach
                }

                context = blockItem.getPlacementContext(context)
                    ?: run {
                        result(PlaceResult.ScaffoldExceeded(pos, context))
                        return@forEach
                    }

                lateinit var resultState: BlockState
                var rot = fakePlayer.rotation

                val simulatePlaceState = placeState@{
                    resultState = blockItem.getPlacementState(context)
                        ?: return@placeState PlaceResult.BlockedByEntity(pos)

                    return@placeState if (!nextTargetState.matches(resultState, pos, preProcessing.ignore))
                        PlaceResult.NoIntegrity(
                            pos,
                            resultState,
                            context,
                            (nextTargetState as? TargetState.State)?.blockState
                        )
                    else null
                }

                val currentDirIsValid = simulatePlaceState()?.let { basePlaceResult ->
                    if (!placeConfig.rotateForPlace) {
                        result(basePlaceResult)
                        return@forEach
                    }
                    false
                } != false

                run rotate@{
                    if (!placeConfig.axisRotate) {
                        fakePlayer.rotation = checkedHit.targetRotation
                        simulatePlaceState()?.let { rotatedPlaceResult ->
                            result(rotatedPlaceResult)
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

                    PlaceDirection.entries.asReversed().forEachIndexed direction@{ index, direction ->
                        fakePlayer.rotation = direction.rotation
                        when (val placeResult = simulatePlaceState()) {
                            is PlaceResult.BlockedByEntity -> {
                                result(placeResult)
                                return@forEach
                            }

                            is PlaceResult.NoIntegrity -> {
                                if (index != PlaceDirection.entries.lastIndex) return@direction
                                result(placeResult)
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

                val rotationRequest = if (placeConfig.axisRotate) {
                    lookInDirection(PlaceDirection.fromRotation(rot))
                } else lookAt(rot, 0.001)

                val placeContext = PlaceContext(
                    blockHit,
                    RotationRequest(rotationRequest, this),
                    player.inventory.selectedSlot,
                    context.blockPos,
                    blockState(context.blockPos),
                    resultState,
                    shouldSneak,
                    false,
                    currentDirIsValid,
                    this
                )

                val selection = optimalStack.item.select()
                val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.HOTBAR) }
                val container = selection.containerWithMaterial(containerSelection).firstOrNull() ?: run {
                    result(
                        GenericResult.WrongItemSelection(
                            pos,
                            placeContext,
                            optimalStack.item.select(),
                            player.mainHandStack
                        )
                    )
                    return
                }
                val stack = selection.filterStacks(container.stacks).run {
                    firstOrNull { player.inventory.getSlotWithStack(it) == player.inventory.selectedSlot }
                        ?: first()
                }

                placeContext.hotbarIndex = player.inventory.getSlotWithStack(stack)

                result(PlaceResult.Place(pos, placeContext))
            }
        }

        return
    }
}