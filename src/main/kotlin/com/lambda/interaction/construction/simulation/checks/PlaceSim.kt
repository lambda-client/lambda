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
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.PlaceResult
import com.lambda.interaction.construction.simulation.ISimInfo
import com.lambda.interaction.construction.simulation.ISimInfo.Companion.sim
import com.lambda.interaction.construction.simulation.Sim
import com.lambda.interaction.construction.simulation.SimDsl
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.simulation.checks.BreakSim.Companion.simBreak
import com.lambda.interaction.construction.simulation.checks.PlaceSim.RotatePlaceTest.Companion.rotatePlaceTest
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.rotation
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.PlaceDirection
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.interaction.request.rotating.visibilty.lookInDirection
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.EntityUtils.getPositionsWithinHitboxXZ
import com.lambda.util.item.ItemStackUtils.inventoryIndex
import com.lambda.util.item.ItemUtils.blockItem
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.minus
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.copyPlayer
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShapes

class PlaceSim private constructor(simInfo: ISimInfo)
    : Sim<PlaceResult>(),
    ISimInfo by simInfo
{
    override fun dependentUpon(buildResult: BuildResult) =
        PlaceResult.Dependency(pos, buildResult)

    companion object {
        context(automatedSafeContext: AutomatedSafeContext, dependent: Sim<*>)
        @SimDsl
        suspend fun SimInfo.simPlacement() =
            PlaceSim(this).run {
                withDependent(dependent) {
                    automatedSafeContext.simPlacements()
                }
            }
    }

    private suspend fun AutomatedSafeContext.simPlacements() =
        supervisorScope {
            preProcessing.sides.forEach { side ->
                launch {
                    val neighborPos = pos.offset(side)
                    val neighborSide = side.opposite
                    if (!placeConfig.airPlace.isEnabled)
                        testBlock(neighborPos, neighborSide, this@supervisorScope)
                    testBlock(pos, side, this@supervisorScope)
                }
            }
        }

    private suspend fun AutomatedSafeContext.testBlock(pos: BlockPos, side: Direction, supervisorScope: CoroutineScope) {
        if (!world.worldBorder.contains(pos)) return

        val testBlockState = blockState(pos)
        val shape = testBlockState.getOutlineShape(world, pos).let { outlineShape ->
            if (!outlineShape.isEmpty || !placeConfig.airPlace.isEnabled) outlineShape
            else VoxelShapes.fullCube()
        }
        if (shape.isEmpty) return

        // ToDo: For each hand
        val fakePlayer = copyPlayer(player).apply {
            val newPos = pov - (this.eyePos - this.pos)
            setPos(newPos.x, newPos.y, newPos.z)
            if (testBlockState.block::class in BlockUtils.interactionBlocks) {
                input.sneaking = true
                updatePose()
            }
        }
        val pov = fakePlayer.eyePos

        val validHits = scanShape(pov, shape, pos, setOf(side), preProcessing) ?: return

        selectHitPos(validHits, fakePlayer, targetState.getStack(this@PlaceSim.pos, state).blockItem, supervisorScope)
    }

    private fun AutomatedSafeContext.getSwapStack(): ItemStack? {
        val optimalStack = targetState.getStack(pos, state)
        val stackSelection = optimalStack.item.select()
        val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.Hotbar) }
        val container = stackSelection.containerWithMaterial(containerSelection).firstOrNull() ?: run {
            result(GenericResult.WrongItemSelection(pos, optimalStack.item.select(), player.mainHandStack))
            return null
        }
        return stackSelection.filterStacks(container.stacks).run {
            firstOrNull { it.inventoryIndex == player.inventory.selectedSlot }
                ?: firstOrNull()
        }
    }

    private suspend fun AutomatedSafeContext.selectHitPos(
        validHits: Collection<CheckedHit>,
        fakePlayer: ClientPlayerEntity,
        item: BlockItem,
        supervisorScope: CoroutineScope
    ) {
        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            val hitResult = checkedHit.hit.blockResult ?: return

            val context = item.getPlacementContext(
                ItemPlacementContext(
                    world,
                    fakePlayer,
                    Hand.MAIN_HAND,
                    item.defaultStack,
                    hitResult,
                )
            ) ?: run {
                result(PlaceResult.ScaffoldExceeded(pos))
                return
            }

            if (context.blockPos != pos) {
                result(PlaceResult.UnexpectedPosition(pos, context.blockPos))
                return
            }

            val cachePos = CachedBlockPosition(context.world, context.blockPos, false)
            if (!player.abilities.allowModifyWorld && !item.defaultStack.canPlaceOn(cachePos)) {
                result(PlaceResult.IllegalUsage(pos))
                return
            }

            if (!context.canPlace()) {
                result(PlaceResult.CantReplace(pos, context))
                return
            }

            val rotatePlaceTest = simRotatePlace(fakePlayer, checkedHit, context) ?: return

            val rotationRequest = if (placeConfig.axisRotate) {
                lookInDirection(PlaceDirection.fromRotation(rotatePlaceTest.rotation))
            } else lookAt(rotatePlaceTest.rotation, 0.001)

            val swapStack = getSwapStack() ?: return
            if (!swapStack.item.isEnabled(world.enabledFeatures)) {
                result(PlaceResult.BlockFeatureDisabled(pos, swapStack))
                supervisorScope.cancel()
                return
            }

            val placeContext = PlaceContext(
                hitResult,
                RotationRequest(rotationRequest, this@PlaceSim),
                swapStack.inventoryIndex,
                pos,
                state,
                rotatePlaceTest.resultState,
                fakePlayer.isSneaking,
                rotatePlaceTest.currentDirIsValid,
                this@PlaceSim
            )

            result(PlaceResult.Place(pos, placeContext))
        }

        return
    }

    private suspend fun AutomatedSafeContext.simRotatePlace(
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

    private suspend fun AutomatedSafeContext.testPlaceState(context: ItemPlacementContext): PlaceTest {
        val resultState = context.stack.blockItem.getPlacementState(context)
            ?: run {
                handleEntityBlockage(context)
                return PlaceTest(state, PlaceTestResult.BlockedByEntity)
            }

        return if (!matchesTarget(resultState, false)) {
            result(PlaceResult.NoIntegrity(pos, resultState, context, (targetState as? TargetState.State)?.blockState))
            PlaceTest(resultState, PlaceTestResult.NoIntegrity)
        } else PlaceTest(resultState, PlaceTestResult.Success)
    }

    private suspend fun AutomatedSafeContext.handleEntityBlockage(context: ItemPlacementContext): List<Entity> {
        val pos = context.blockPos
        val theoreticalState = context.stack.blockItem.block.getPlacementState(context)
            ?: return emptyList()

        val collisionShape = theoreticalState.getCollisionShape(
            world, pos, ShapeContext.ofPlacement(player)
        ).offset(pos)

        val collidingEntities = collisionShape.boundingBoxes.flatMap { box ->
            world.entities.filter { it.boundingBox.intersects(box) }
        }

        if (collidingEntities.isNotEmpty()) {
            collidingEntities
                .mapNotNull { entity ->
                    if (entity === player) {
                        result(PlaceResult.BlockedBySelf(pos))
                        return@mapNotNull null
                    }
                    val hitbox = entity.boundingBox
                    entity.getPositionsWithinHitboxXZ(
                        (pos.y - (hitbox.maxY - hitbox.minY)).floorToInt(),
                        pos.y
                    )
                }
                .flatten()
                .forEach { support ->
                    sim(support, blockState(support), TargetState.Empty) { simBreak() }
                }
            result(PlaceResult.BlockedByEntity(pos, collidingEntities, context.hitPos, context.side))
        }

        return collidingEntities
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