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
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.PlaceResult
import com.lambda.interaction.construction.simulation.ISimInfo
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimCheckerDsl
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
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemStackUtils.inventoryIndex
import com.lambda.util.item.ItemUtils.blockItem
import com.lambda.util.math.distSq
import com.lambda.util.math.vec3d
import com.lambda.util.player.copyPlayer
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import net.minecraft.block.BlockState
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.pow

class PlaceChecker @SimCheckerDsl private constructor(simInfo: SimInfo)
    : SimChecker<PlaceResult>(), Dependable,
    ISimInfo by simInfo
{
    lateinit var resultState: BlockState
    var rot = RotationManager.serverRotation

    private val swapStack by lazy {
        runSafeAutomated {
            val optimalStack = targetState.getStack(pos)
            val stackSelection = optimalStack.item.select()
            val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.HOTBAR) }
            val container = stackSelection.containerWithMaterial(containerSelection).firstOrNull() ?: run {
                result(
                    GenericResult.WrongItemSelection(
                        pos,
                        optimalStack.item.select(),
                        player.mainHandStack
                    )
                )
                return@runSafeAutomated ItemStack(Items.AIR)
            }
            return@runSafeAutomated stackSelection.filterStacks(container.stacks).run {
                firstOrNull { it.inventoryIndex == player.inventory.selectedSlot }
                    ?: first()
            }
        } ?: ItemStack(Items.AIR)
    }
    private val blockItem get() = swapStack.blockItem

    private var currentDirIsValid = false

    override fun asDependent(buildResult: BuildResult) =
        PlaceResult.Dependency(pos, buildResult)

    companion object {
        @SimCheckerDsl
        context(automatedSafeContext: AutomatedSafeContext, dependable: Dependable?)
        suspend fun SimInfo.checkPlacements() =
            PlaceChecker(this).run {
                checkDependent(dependable)
                automatedSafeContext.checkPlacements()
            }
    }

    private suspend fun AutomatedSafeContext.checkPlacements(): Boolean {
        if (targetState.isEmpty()) return false

        supervisorScope {
            withContext(Dispatchers.Default) {
                preProcessing.sides.map { side ->
                    launch {
                        val neighborPos = pos.offset(side)
                        val neighborSide = side.opposite
                        if (!placeConfig.airPlace.isEnabled)
                            testBlock(neighborPos, neighborSide, this@supervisorScope)
                        testBlock(pos, side, this@supervisorScope)
                    }
                }.joinAll()
            }
        }

        return true
    }

    private suspend fun AutomatedSafeContext.testBlock(pos: BlockPos, side: Direction, supervisorScope: CoroutineScope) {
        if (!world.worldBorder.contains(pos)) return

        val voxelShape = blockState(pos).getOutlineShape(world, pos).let { outlineShape ->
            if (!outlineShape.isEmpty || !placeConfig.airPlace.isEnabled) outlineShape
            else VoxelShapes.fullCube()
        }
        if (voxelShape.isEmpty) return

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }
        val verify: CheckedHit.() -> Boolean = {
            hit.blockResult?.blockPos == pos && hit.blockResult?.side == side
        }

        val validHits = mutableListOf<CheckedHit>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = buildConfig.interactReach.pow(2)

        withContext(Dispatchers.Default) {
            boxes.map { box ->
                launch {
                    val sides = if (buildConfig.checkSideVisibility) {
                        box.getVisibleSurfaces(eye).intersect(setOf(side))
                    } else setOf(side)

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
                                    BlockHitResult(hitVec, side, pos, false)
                                }

                                else -> null
                            }
                        } else {
                            val hitVec = newRotation.castBox(box, buildConfig.interactReach, eye)
                            BlockHitResult(hitVec, side, pos, false)
                        } ?: return@scanSurfaces

                        val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)
                        if (!checked.verify()) return@scanSurfaces

                        validHits.add(checked)
                    }
                }
            }.joinAll()
        }

        if (validHits.isEmpty()) {
            if (misses.isNotEmpty()) {
                result(GenericResult.OutOfReach(pos, eye, misses))
                return
            }

            result(GenericResult.NotVisible(pos, pos, side, eye.distanceTo(pos.offset(side).vec3d)))
            return
        }

        if (swapStack.item == Items.AIR)
            supervisorScope.cancel()
        else if (!swapStack.item.isEnabled(world.enabledFeatures)) {
            result(PlaceResult.BlockFeatureDisabled(pos, swapStack))
            supervisorScope.cancel()
        } else selectHitPos(validHits)
    }

    private fun AutomatedSafeContext.selectHitPos(validHits: List<CheckedHit>) {
        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            // ToDo: For each hand and sneak or not?
            val fakePlayer = copyPlayer(player).apply {
                this.rotation = RotationManager.serverRotation
            }

            val blockHit = checkedHit.hit.blockResult ?: return

            // ToDo: Override the stack used for this to account for blocks where replaceability is dependent on the held item
            val usageContext = ItemUsageContext(
                world,
                fakePlayer,
                Hand.MAIN_HAND,
                swapStack,
                blockHit,
            )
            val cachePos = CachedBlockPosition(
                usageContext.world, usageContext.blockPos, false
            )

            if (!player.abilities.allowModifyWorld && !swapStack.canPlaceOn(cachePos)) {
                result(PlaceResult.IllegalUsage(pos))
                return
            }

            var context = ItemPlacementContext(usageContext)

            if (context.blockPos != pos) {
                result(PlaceResult.UnexpectedPosition(pos, context.blockPos))
                return
            }

            if (!context.canPlace()) {
                result(PlaceResult.CantReplace(pos, context))
                return
            }

            context = swapStack.blockItem.getPlacementContext(context) ?: run {
                result(PlaceResult.ScaffoldExceeded(pos, context))
                return
            }

            if (!simRotation(fakePlayer, checkedHit, context)) return

            val hitBlock = blockState(blockHit.blockPos).block
            val shouldSneak = hitBlock::class in BlockUtils.interactionBlocks

            val rotationRequest = if (placeConfig.axisRotate) {
                lookInDirection(PlaceDirection.fromRotation(rot))
            } else lookAt(rot, 0.001)

            val placeContext = PlaceContext(
                blockHit,
                RotationRequest(rotationRequest, this@PlaceChecker),
                swapStack.inventoryIndex,
                context.blockPos,
                blockState(context.blockPos),
                resultState,
                shouldSneak,
                false,
                currentDirIsValid,
                this@PlaceChecker
            )

            result(PlaceResult.Place(pos, placeContext))
        }

        return
    }

    private fun SafeContext.simRotation(
        fakePlayer: ClientPlayerEntity,
        checkedHit: CheckedHit,
        context: ItemPlacementContext
    ): Boolean {
        currentDirIsValid = if (testPlaceState(pos, targetState, context) != PlaceTestResult.Success) {
            if (!placeConfig.rotateForPlace) return false
            else false
        } else true

        if (!placeConfig.axisRotate) {
            fakePlayer.rotation = checkedHit.targetRotation
            if (testPlaceState(pos, targetState, context) != PlaceTestResult.Success) return false
            rot = fakePlayer.rotation
            return true
        }

        fakePlayer.rotation = player.rotation
        if (testPlaceState(pos, targetState, context) == PlaceTestResult.Success) {
            rot = fakePlayer.rotation
            return true
        }

        PlaceDirection.entries.asReversed().forEachIndexed direction@{ index, direction ->
            fakePlayer.rotation = direction.rotation
            when (testPlaceState(pos, targetState, context)) {
                PlaceTestResult.BlockedByEntity -> return@direction

                PlaceTestResult.NoIntegrity -> {
                    if (index != PlaceDirection.entries.lastIndex) return@direction
                    return false
                }

                else -> {
                    rot = fakePlayer.rotation
                    return true
                }
            }
        }

        return true
    }

    private fun SafeContext.testPlaceState(
        pos: BlockPos,
        targetState: TargetState,
        context: ItemPlacementContext
    ): PlaceTestResult {
        resultState = blockItem.getPlacementState(context) ?: run {
            result(PlaceResult.BlockedByEntity(pos))
            return PlaceTestResult.BlockedByEntity
        }

        return if (!targetState.matches(resultState, pos, preProcessing.ignore)) {
            result(PlaceResult.NoIntegrity(pos, resultState, context, (targetState as? TargetState.State)?.blockState))
            PlaceTestResult.NoIntegrity
        } else PlaceTestResult.Success
    }

    private enum class PlaceTestResult {
        Success,
        BlockedByEntity,
        NoIntegrity
    }
}