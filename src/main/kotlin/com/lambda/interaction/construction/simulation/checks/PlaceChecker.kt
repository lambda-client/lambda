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
import com.lambda.interaction.construction.simulation.checks.PlaceChecker.RotatePlaceTest.Companion.rotatePlaceTest
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.Rotation
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
import com.lambda.util.player.MovementUtils.sneaking
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

        val testBlockState = blockState(pos)
        val voxelShape = testBlockState.getOutlineShape(world, pos).let { outlineShape ->
            if (!outlineShape.isEmpty || !placeConfig.airPlace.isEnabled) outlineShape
            else VoxelShapes.fullCube()
        }
        if (voxelShape.isEmpty) return

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }

        val validHits = mutableListOf<CheckedHit>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = buildConfig.interactReach.pow(2)

        // ToDo: For each hand
        val fakePlayer = copyPlayer(player).apply {
            if (testBlockState.block::class in BlockUtils.interactionBlocks) {
                input.sneaking = true
                updatePose()
            }
        }

        val eye = fakePlayer.eyePos

        withContext(Dispatchers.Default) {
            boxes.map { box ->
                launch {
                    val sides = if (buildConfig.checkSideVisibility || buildConfig.strictRayCast) {
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
                            newRotation.rayCast(buildConfig.interactReach, eye)?.blockResult ?: return@scanSurfaces
                        } else {
                            val hitVec = newRotation.castBox(box, buildConfig.interactReach, eye) ?: return@scanSurfaces
                            BlockHitResult(hitVec, side, pos, false)
                        }

                        if (hit.blockPos != pos || hit.side != side) return@scanSurfaces
                        val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)

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
        } else selectHitPos(validHits, fakePlayer)
    }

    private fun AutomatedSafeContext.selectHitPos(validHits: List<CheckedHit>, fakePlayer: ClientPlayerEntity) {
        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            val hitResult = checkedHit.hit.blockResult ?: return

            var context = ItemPlacementContext(
                world,
                fakePlayer,
                Hand.MAIN_HAND,
                swapStack,
                hitResult,
            )

            if (context.blockPos != pos) {
                result(PlaceResult.UnexpectedPosition(pos, context.blockPos))
                return
            }

            val cachePos = CachedBlockPosition(context.world, context.blockPos, false)
            if (!player.abilities.allowModifyWorld && !swapStack.canPlaceOn(cachePos)) {
                result(PlaceResult.IllegalUsage(pos))
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

            val rotatePlaceTest = simRotatePlace(fakePlayer, checkedHit, context) ?: return

            val rotationRequest = if (placeConfig.axisRotate) {
                lookInDirection(PlaceDirection.fromRotation(rotatePlaceTest.rotation))
            } else lookAt(rotatePlaceTest.rotation, 0.001)

            val placeContext = PlaceContext(
                hitResult,
                RotationRequest(rotationRequest, this@PlaceChecker),
                swapStack.inventoryIndex,
                pos,
                state,
                rotatePlaceTest.resultState,
                fakePlayer.isSneaking,
                false,
                rotatePlaceTest.currentDirIsValid,
                this@PlaceChecker
            )

            result(PlaceResult.Place(pos, placeContext))
        }

        return
    }

    private fun SafeContext.simRotatePlace(
        fakePlayer: ClientPlayerEntity,
        checkedHit: CheckedHit,
        context: ItemPlacementContext
    ): RotatePlaceTest? {
        fakePlayer.rotation = RotationManager.serverRotation
        val currentDirIsValid = testPlaceState(context).testResult == PlaceTestResult.Success

        if (!placeConfig.axisRotate) {
            fakePlayer.rotation = checkedHit.targetRotation
            return rotatePlaceTest(testPlaceState(context), currentDirIsValid, fakePlayer.rotation)
        }

        fakePlayer.rotation = player.rotation
        testPlaceState(context).takeIf { it.testResult == PlaceTestResult.Success }?.let { playerRotTest ->
            return rotatePlaceTest(playerRotTest, currentDirIsValid, fakePlayer.rotation)
        }

        PlaceDirection.entries.asReversed().forEach direction@{ direction ->
            fakePlayer.rotation = direction.rotation
            testPlaceState(context).takeIf { it.testResult == PlaceTestResult.Success }?.let { axisRotateTest ->
                return rotatePlaceTest(axisRotateTest, currentDirIsValid, fakePlayer.rotation)
            }
        }

        return null
    }

    private fun SafeContext.testPlaceState(context: ItemPlacementContext): PlaceTest {
        val resultState = blockItem.getPlacementState(context) ?: run {
            result(PlaceResult.BlockedByEntity(pos))
            return PlaceTest(state, PlaceTestResult.BlockedByEntity)
        }

        return if (!targetState.matches(resultState, pos, preProcessing.ignore)) {
            result(PlaceResult.NoIntegrity(pos, resultState, context, (targetState as? TargetState.State)?.blockState))
            PlaceTest(resultState, PlaceTestResult.NoIntegrity)
        } else PlaceTest(resultState, PlaceTestResult.Success)
    }

    private class RotatePlaceTest private constructor(
        val resultState: BlockState,
        val currentDirIsValid: Boolean,
        val rotation: Rotation
    ) {
        companion object {
            fun rotatePlaceTest(
                placeTest: PlaceTest,
                currentDirIsValid: Boolean,
                rotation: Rotation
            ): RotatePlaceTest? {
                return if (placeTest.testResult != PlaceTestResult.Success) null
                else RotatePlaceTest(placeTest.resultState, currentDirIsValid, rotation)
            }
        }
    }
    private data class PlaceTest(val resultState: BlockState, val testResult: PlaceTestResult)
    private enum class PlaceTestResult {
        Success,
        BlockedByEntity,
        NoIntegrity
    }
}