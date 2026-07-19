
package com.minato.interaction.construction.simulation.checks

import com.minato.context.AutomatedSafeContext
import com.minato.interaction.construction.simulation.BreakSimInfo
import com.minato.interaction.construction.simulation.Sim
import com.minato.interaction.construction.simulation.SimDsl
import com.minato.interaction.construction.simulation.SimInfo
import com.minato.interaction.construction.simulation.SimInfo.Companion.sim
import com.minato.interaction.construction.simulation.context.BreakContext
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.results.BreakResult
import com.minato.interaction.construction.simulation.result.results.GenericResult
import com.minato.interaction.construction.verify.TargetState
import com.minato.interaction.handlers.ContainerHandler.findContainersWithMaterial
import com.minato.interaction.managers.hotbar.HotbarManager
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.interaction.material.ContainerSelection.Companion.selectContainer
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.StackSelection.Companion.EVERYTHING
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.BlockUtils.blockState
import com.minato.util.BlockUtils.calcItemBlockBreakingDelta
import com.minato.util.BlockUtils.instantBreakable
import com.minato.util.item.ItemStackUtils.inventoryIndex
import com.minato.util.item.ItemStackUtils.inventoryIndexOrSelected
import com.minato.util.player.RotationUtils.lookAtBlock
import com.minato.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.block.Waterloggable
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.enchantment.Enchantments
import net.minecraft.fluid.FlowableFluid
import net.minecraft.fluid.LavaFluid
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.jvm.optionals.getOrNull

class BreakSim private constructor(simInfo: SimInfo)
	: Sim<BreakResult>(),
	SimInfo by simInfo
{
	override fun dependentUpon(buildResult: BuildResult) =
		BreakResult.Dependency(pos, buildResult)

	companion object {
		@SimDsl
		context(automatedSafeContext: AutomatedSafeContext, dependent: Sim<*>)
		suspend fun BreakSimInfo.simBreak() =
			BreakSim(this).run {
				withDependent(dependent) {
					automatedSafeContext.simBreaks()
				}
			}
	}

	private suspend fun AutomatedSafeContext.simBreaks() {
		if (!world.worldBorder.contains(pos)) {
			result(BreakResult.OutOfBorder(pos))
			return
		}

		if (breakConfig.avoidSupporting) player.supportingBlockPos.getOrNull()?.let { support ->
			if (support != pos) return@let
			result(BreakResult.PlayerOnTop(pos, state))
			return
		}

		if (!state.fluidState.isEmpty && state.isReplaceable) {
			result(BreakResult.Submerge(pos, state))
			sim(pos, state, TargetState.Solid(emptySet()))
			return
		}

		if (breakConfig.avoidFluids && affectsFluids()) return

		val (swapStack, stackSelection) = getSwapStack() ?: return
		val instant = instantBreakable(
			state, pos,
			if (breakConfig.swapMode.isEnabled()) swapStack else player.mainHandStack,
			breakConfig.breakThreshold
		)

		val shape = state.getOutlineShape(world, pos)

		if (shape.boundingBoxes.map { it.offset(pos) }.any { it.contains(pov) }) {
			val currentCast = RotationManager.activeRotation.rayCast(buildConfig.blockReach, pov)
			currentCast?.blockResult?.let { blockHit ->
				val rotationRequest = lookAtBlock(pos)?.rotation?.let { rotationRequest { rotation(it) } } ?: return
				val breakContext = BreakContext(
					blockHit,
					rotationRequest,
					swapStack.inventoryIndexOrSelected,
					stackSelection,
					instant,
					true,
					state,
					this
				)
				result(BreakResult.Break(pos, breakContext))
			}
			return
		}

		val validHits = scanShape(pov, shape, pos, Direction.entries.toSet(), null) ?: return

		val bestHit = buildConfig.pointSelection.select(validHits) ?: return
		val rotationRequest = rotationRequest { rotation(bestHit.rotation) }

		val breakContext = BreakContext(
			bestHit.hit.blockResult ?: return,
			rotationRequest,
			swapStack.inventoryIndexOrSelected,
			stackSelection,
			instant,
			false,
			state,
			this
		)

		result(BreakResult.Break(pos, breakContext))
		return
	}

	private fun AutomatedSafeContext.getSwapStack(): Pair<ItemStack, StackSelection>? {
		// Stack size 0 to account for attacking with an empty hand. Empty slots have stack size 0
		val stackSelection = selectStack(
			count = 0,
			sorter = compareByDescending<ItemStack> {
				it.canBreak(CachedBlockPosition(world, pos, false))
			}.thenByDescending {
				state.calcItemBlockBreakingDelta(pos, it)
			}.thenByDescending {
				it.inventoryIndex == HotbarManager.serverSlot
			}
		) {
			EVERYTHING
				.andIf(breakConfig.efficientOnly) {
					isEfficientForBreaking(state)
				}.andIf(breakConfig.suitableToolsOnly) {
					isSuitableForBreaking(state)
				}.andIf(breakConfig.forceSilkTouch) {
					hasEnchantment(Enchantments.SILK_TOUCH)
				}.andIf(breakConfig.forceFortunePickaxe) {
					hasEnchantment(Enchantments.FORTUNE)
				}
		}

		val silentSwapSelection = selectContainer {
			ofAnyType(MaterialContainer.Rank.Hotbar)
		}

		val hotbarCandidates = stackSelection
			.findContainersWithMaterial(silentSwapSelection)
			.flatMap { it.matchingStacks(stackSelection) }
		if (hotbarCandidates.isEmpty()) {
			result(GenericResult.WrongItemSelection(pos, stackSelection, player.mainHandStack))
			return null
		}

		var bestStack = ItemStack.EMPTY
		var bestBreakDelta = -1f
		hotbarCandidates.forEach { stack ->
			val breakDelta = state.calcItemBlockBreakingDelta(pos, stack)
			if (breakDelta > bestBreakDelta ||
				(stack == player.mainHandStack && breakDelta >= bestBreakDelta)
			) {
				bestBreakDelta = breakDelta
				bestStack = stack
			}
		}
		return if (bestBreakDelta == -1f) null
		else Pair(bestStack, stackSelection)
	}

	private suspend fun AutomatedSafeContext.affectsFluids(): Boolean {
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
					accumulator[offsetPos] = offsetState
					return@fold accumulator
				}

				if (offsetState.block is Waterloggable) {
					accumulator[offsetPos] = offsetState
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

		if (affectedFluids.isNotEmpty()) {
			if (breakConfig.fillFluids) {
				affectedFluids.forEach { (fluidPos, fluidState) ->
					result(BreakResult.Submerge(fluidPos, fluidState))
					sim(fluidPos, fluidState, TargetState.Solid(emptySet()))
				}
			}
			result(BreakResult.BlockedByFluid(pos, state, affectedFluids.keys))
			return true
		}

		return false
	}
}