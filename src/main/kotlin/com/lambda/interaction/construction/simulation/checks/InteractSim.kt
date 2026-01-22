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
import com.lambda.interaction.construction.simulation.InteractSimInfo
import com.lambda.interaction.construction.simulation.Sim
import com.lambda.interaction.construction.simulation.SimDsl
import com.lambda.interaction.construction.simulation.SimInfo.Companion.sim
import com.lambda.interaction.construction.simulation.context.InteractContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.results.GenericResult
import com.lambda.interaction.construction.simulation.result.results.InteractResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotation
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.managers.rotating.visibilty.PlaceDirection
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.managers.rotating.visibilty.lookInDirection
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.findContainersWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.EntityUtils.getPositionsWithinHitboxXZ
import com.lambda.util.item.ItemStackUtils.inventoryIndex
import com.lambda.util.item.ItemUtils.blockItem
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.minus
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.SlotUtils.hotbarStacks
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
import net.minecraft.item.Item
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.RotationPropertyHelper
import net.minecraft.util.shape.VoxelShapes

class InteractSim private constructor(simInfo: InteractSimInfo)
    : Sim<InteractResult>(),
    InteractSimInfo by simInfo
{
    override fun dependentUpon(buildResult: BuildResult) =
        InteractResult.Dependency(pos, buildResult)

    companion object {
        context(automatedSafeContext: AutomatedSafeContext, dependent: Sim<*>)
        @SimDsl
        suspend fun InteractSimInfo.simInteraction() =
            InteractSim(this).run {
                withDependent(dependent) {
                    automatedSafeContext.simInteraction()
                }
            }
    }

    private suspend fun AutomatedSafeContext.simInteraction() =
        supervisorScope {
            preProcessing.info.sides.forEach { side ->
                val neighborPos = pos.offset(side)
                val neighborSide = side.opposite
	            launch { testBlock(pos, side, this@supervisorScope) }
	            if (placing) launch { testBlock(neighborPos, neighborSide, this@supervisorScope) }
            }
        }

    private suspend fun AutomatedSafeContext.testBlock(pos: BlockPos, side: Direction, supervisorScope: CoroutineScope) {
        if (!world.worldBorder.contains(pos)) return

        val testBlockState = blockState(pos)
        val shape = testBlockState.getOutlineShape(world, pos).let { outlineShape ->
            if (!outlineShape.isEmpty || !interactConfig.airPlace.isEnabled) outlineShape
            else VoxelShapes.fullCube()
        }
        if (shape.isEmpty) return

        val fakePlayer = copyPlayer(player).apply {
            val newPos = pov - (this.eyePos - this.pos)
            setPos(newPos.x, newPos.y, newPos.z)
	        if (preProcessing.info.sneak == false) {
				if (testBlockState.block::class in BlockUtils.interactionBlocks) return
				input.sneaking = false
		        updatePose()
	        } else {
				val shouldNotInteract = testBlockState.block::class in BlockUtils.interactionBlocks && preProcessing.info.placing
		        if (shouldNotInteract || preProcessing.info.sneak == true) {
			        input.sneaking = true
			        updatePose()
		        }
            }
        }
        val pov = fakePlayer.eyePos

        val validHits = scanShape(pov, shape, pos, setOf(side), preProcessing) ?: return

        selectHitPos(validHits, fakePlayer, supervisorScope)
    }

    private suspend fun AutomatedSafeContext.selectHitPos(
        validHits: Collection<CheckedHit>,
        fakePlayer: ClientPlayerEntity,
        supervisorScope: CoroutineScope
    ) {
        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            val hitResult = checkedHit.hit.blockResult ?: return

	        if (!placing) {
		        val interactContext = InteractContext(
			        hitResult,
			        rotationRequest { rotation(checkedHit.rotation) },
			        getSwapStack(item, supervisorScope)?.inventoryIndex ?: return,
			        pos,
			        state,
			        expectedState,
			        preProcessing.info,
			        fakePlayer.isSneaking,
			        true,
			        this@InteractSim
		        )
		        result(InteractResult.Interact(pos, interactContext))
		        return
	        }

	        val blockItem = item as BlockItem

            val context = blockItem.getPlacementContext(
                ItemPlacementContext(
                    world,
                    fakePlayer,
                    Hand.MAIN_HAND,
                    blockItem.defaultStack,
                    hitResult,
                )
            ) ?: run {
                result(InteractResult.ScaffoldExceeded(pos))
                return
            }

            if (context.blockPos != pos) {
                result(InteractResult.UnexpectedPosition(pos, context.blockPos))
                return
            }

            val cachePos = CachedBlockPosition(context.world, context.blockPos, false)
            if (!player.abilities.allowModifyWorld && !blockItem.defaultStack.canPlaceOn(cachePos)) {
                result(InteractResult.IllegalUsage(pos))
                return
            }

            if (!context.canPlace()) {
                result(InteractResult.CantReplace(pos, context))
                return
            }

            val rotatePlaceTest = simRotation(fakePlayer, checkedHit, context) ?: return

            val rotationRequest = if (interactConfig.axisRotate && !expectedState.contains(Properties.ROTATION))
                lookInDirection(PlaceDirection.fromRotation(rotatePlaceTest.rotation))
            else rotatePlaceTest.rotation

            val interactContext = InteractContext(
                hitResult,
                rotationRequest { rotation(rotationRequest) },
	            getSwapStack(item, supervisorScope)?.inventoryIndex ?: return,
                pos,
                state,
                rotatePlaceTest.resultState,
	            preProcessing.info,
                fakePlayer.isSneaking,
                rotatePlaceTest.currentDirIsValid,
                this@InteractSim
            )

            result(InteractResult.Interact(pos, interactContext))
        }

        return
    }

	private fun AutomatedSafeContext.getSwapStack(item: Item?, supervisorScope: CoroutineScope): ItemStack? {
		if (item?.isEnabled(world.enabledFeatures) == false) {
			result(InteractResult.BlockFeatureDisabled(pos, item))
			supervisorScope.cancel()
			return null
		}
		val stackSelection = item?.select()
			?: StackSelection.selectStack(0, sorter = compareByDescending { it.inventoryIndex == player.inventory.selectedSlot })
		val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.Hotbar) }
		val container = stackSelection.findContainersWithMaterial(containerSelection).firstOrNull() ?: run {
			result(GenericResult.WrongItemSelection(pos, stackSelection, player.mainHandStack))
			return null
		}
		val hotbarStacks = player.hotbarStacks
		return stackSelection.filterStacks(container.stacks).run {
			firstOrNull { hotbarStacks.indexOf(it) == player.inventory.selectedSlot }
				?: firstOrNull()
		}
	}

    private suspend fun AutomatedSafeContext.simRotation(
        fakePlayer: ClientPlayerEntity,
        checkedHit: CheckedHit,
        context: ItemPlacementContext
    ): RotatePlaceTest? {
        fakePlayer.rotation = RotationManager.serverRotation
        val currentDirIsValid = testPlaceState(context) != null

        if (!interactConfig.axisRotate) {
            fakePlayer.rotation = checkedHit.rotation
            return testPlaceState(context)?.let { RotatePlaceTest(it, currentDirIsValid, fakePlayer.rotation) }
        }

        fakePlayer.rotation = player.rotation
        testPlaceState(context)?.let { playerRotTest ->
            return RotatePlaceTest(playerRotTest, currentDirIsValid, fakePlayer.rotation)
        }

	    if (Properties.ROTATION in expectedState) {
		    val rotation = expectedState.get(Properties.ROTATION)
		    fakePlayer.yaw = RotationPropertyHelper.toDegrees(rotation)
		    listOf(rotation, rotation + 8).forEach { yaw ->
			    listOf(90f, 0f, -90f).forEach { pitch ->
				    fakePlayer.rotation = Rotation(RotationPropertyHelper.toDegrees(yaw), pitch)
				    testPlaceState(context)?.let { axisRotateTest ->
					    return RotatePlaceTest(axisRotateTest, currentDirIsValid, fakePlayer.rotation)
				    }
			    }
		    }
	    }

        PlaceDirection.entries.asReversed().forEach direction@{ direction ->
            fakePlayer.rotation = direction.rotation
            testPlaceState(context)?.let { axisRotateTest ->
                return RotatePlaceTest(axisRotateTest, currentDirIsValid, fakePlayer.rotation)
            }
        }

        return null
    }

    private suspend fun AutomatedSafeContext.testPlaceState(context: ItemPlacementContext): BlockState? {
        val resultState = (context.stack.blockItem ?: return null).getPlacementState(context)
            ?: run {
                handleEntityBlockage(context)
                return null
            }

        return if (!matchesTarget(resultState, false)) {
            result(InteractResult.NoIntegrity(pos, expectedState, context, resultState))
            null
        } else resultState
    }

    private suspend fun AutomatedSafeContext.handleEntityBlockage(context: ItemPlacementContext): List<Entity> {
        val pos = context.blockPos
        val theoreticalState = (context.stack.blockItem ?: return emptyList()).block.getPlacementState(context)
            ?: return emptyList()

        val collisionShape = theoreticalState.getCollisionShape(
            world, pos, ShapeContext.ofPlacement(player)
        ).offset(pos)

        val collidingEntities = collisionShape.boundingBoxes.flatMap { box ->
            world.entities.filter { it.boundingBox.intersects(box) }
        }

        if (collidingEntities.isNotEmpty()) {
            collidingEntities
                .takeIf { buildConfig.spleefEntities }
                ?.run {
                    mapNotNull { entity ->
                        if (entity === player) {
                            result(InteractResult.BlockedBySelf(pos))
                            return@mapNotNull null
                        }
                        val hitbox = entity.boundingBox
                        entity.getPositionsWithinHitboxXZ(
                            (pos.y - (hitbox.maxY - hitbox.minY)).floorToInt(),
                            pos.y
                        )
                    }.flatten()
						.forEach { support ->
                            sim(support, blockState(support), TargetState.Empty)
                        }
                }
            result(InteractResult.BlockedByEntity(pos, collidingEntities, context.hitPos, context.side))
        }

        return collidingEntities
    }

    private class RotatePlaceTest(val resultState: BlockState, val currentDirIsValid: Boolean, val rotation: Rotation)
}