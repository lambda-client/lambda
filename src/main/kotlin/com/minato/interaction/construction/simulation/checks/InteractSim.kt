
package com.minato.interaction.construction.simulation.checks

import com.minato.context.AutomatedSafeContext
import com.minato.interaction.construction.simulation.InteractSimInfo
import com.minato.interaction.construction.simulation.Sim
import com.minato.interaction.construction.simulation.SimDsl
import com.minato.interaction.construction.simulation.SimInfo.Companion.sim
import com.minato.interaction.construction.simulation.context.InteractContext
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.results.GenericResult
import com.minato.interaction.construction.simulation.result.results.InteractResult
import com.minato.interaction.construction.verify.TargetState
import com.minato.interaction.handlers.ContainerHandler.findContainersWithMaterial
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.Rotation
import com.minato.interaction.managers.rotating.Rotation.Companion.rotation
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.interaction.material.ContainerSelection.Companion.selectContainer
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.BlockUtils.blockState
import com.minato.util.EntityUtils.getPositionsWithinHitboxXZ
import com.minato.util.PlaceDirection
import com.minato.util.item.ItemStackUtils.inventoryIndex
import com.minato.util.item.ItemUtils.blockItem
import com.minato.util.math.MathUtils.floorToInt
import com.minato.util.math.minus
import com.minato.util.player.CheckedHit
import com.minato.util.player.MovementUtils.sneaking
import com.minato.util.player.PlayerUtils.copyPlayer
import com.minato.util.player.RotationUtils.lookInDirection
import com.minato.util.world.raycast.RayCastUtils.blockResult
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
import net.minecraft.item.Items
import net.minecraft.screen.slot.Slot
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
		@SimDsl
		context(automatedSafeContext: AutomatedSafeContext, dependent: Sim<*>)
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
			val prevSneak = input.sneaking
			val interacting = !preProcessing.info.placing
			val sneak = preProcessing.info.sneak
			if (interacting && (sneak == true || (sneak == null && prevSneak))) return
			input.sneaking = preProcessing.info.sneak ?: prevSneak && !interacting
			if (prevSneak != isSneaking) updatePose()
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
					getSwapSlot(item, supervisorScope)?.index ?: return,
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
				getSwapSlot(item, supervisorScope)?.index ?: return,
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

	private fun AutomatedSafeContext.getSwapSlot(item: Item?, supervisorScope: CoroutineScope): Slot? {
		if (item?.isEnabled(world.enabledFeatures) == false) {
			result(InteractResult.BlockFeatureDisabled(pos, item))
			supervisorScope.cancel()
			return null
		}
		val stackSelection = item?.select()?.apply { if (item == Items.AIR) count = 0 }
			?: StackSelection.selectStack(0, sorter = compareByDescending { it.inventoryIndex == player.inventory.selectedSlot })
		val containerSelection = selectContainer { ofAnyType(MaterialContainer.Rank.Hotbar) }
		val container = stackSelection.findContainersWithMaterial(containerSelection).firstOrNull() ?: run {
			result(GenericResult.WrongItemSelection(pos, stackSelection, player.mainHandStack))
			return null
		}
		return stackSelection.filterSlots(container.slots).run {
			firstOrNull { it.index == player.inventory.selectedSlot }
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