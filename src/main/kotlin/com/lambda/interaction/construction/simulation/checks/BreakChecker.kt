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
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.result.results.BreakResult
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.simulation.ISimInfo
import com.lambda.interaction.construction.simulation.ISimInfo.Companion.simInfo
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimCheckerDsl
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.simulation.checks.PlaceChecker.Companion.checkPlacements
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.EVERYTHING
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.interaction.request.rotating.visibilty.lookAtBlock
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.item.ItemStackUtils.inventoryIndex
import com.lambda.util.math.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.block.Waterloggable
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.enchantment.Enchantments
import net.minecraft.fluid.FlowableFluid
import net.minecraft.fluid.LavaFluid
import net.minecraft.fluid.WaterFluid
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.ItemTags.DIAMOND_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.GOLD_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.IRON_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.NETHERITE_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.STONE_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.WOODEN_TOOL_MATERIALS
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull
import kotlin.math.pow

class BreakChecker @SimCheckerDsl private constructor(simInfo: SimInfo)
    : SimChecker<BreakResult>(), Dependable,
    ISimInfo by simInfo
{
    val stackSelection: StackSelection by lazy {
        runSafe {
            selectStack(
                sorter = compareByDescending<ItemStack> {
                    it.canBreak(CachedBlockPosition(world, pos, false))
                }.thenByDescending {
                    state.calcItemBlockBreakingDelta(pos, it)
                }
            ) {
                EVERYTHING
                    .andIf(breakConfig.suitableToolsOnly) {
                        isSuitableForBreaking(state)
                    }.andIf(breakConfig.forceSilkTouch) {
                        hasEnchantment(Enchantments.SILK_TOUCH)
                    }.andIf(breakConfig.forceFortunePickaxe) {
                        hasEnchantment(Enchantments.FORTUNE)
                    }.andIf(!breakConfig.useWoodenTools) {
                        hasTag(WOODEN_TOOL_MATERIALS).not()
                    }.andIf(!breakConfig.useStoneTools) {
                        hasTag(STONE_TOOL_MATERIALS).not()
                    }.andIf(!breakConfig.useIronTools) {
                        hasTag(IRON_TOOL_MATERIALS).not()
                    }.andIf(!breakConfig.useDiamondTools) {
                        hasTag(DIAMOND_TOOL_MATERIALS).not()
                    }.andIf(!breakConfig.useGoldTools) {
                        hasTag(GOLD_TOOL_MATERIALS).not()
                    }.andIf(!breakConfig.useNetheriteTools) {
                        hasTag(NETHERITE_TOOL_MATERIALS).not()
                    }
            }
        } ?: EVERYTHING.select()
    }

    override fun asDependent(buildResult: BuildResult) =
        BreakResult.Dependency(pos, buildResult)

    companion object {
        @SimCheckerDsl
        context(automatedSafeContext: AutomatedSafeContext, dependable: Dependable?)
        suspend fun SimInfo.checkBreaks() =
            BreakChecker(this).run {
                checkDependent(dependable)
                automatedSafeContext.checkBreaks()
            }
    }

    private suspend fun AutomatedSafeContext.checkBreaks(): Boolean {
        if (!targetState.isEmpty()) return false

        /* player is standing on top of the block */
        if (breakConfig.avoidSupporting) player.supportingBlockPos.getOrNull()?.let { support ->
            if (support != pos) return@let
            result(BreakResult.PlayerOnTop(pos, state))
            return true
        }

        /* liquid needs to be submerged first to be broken */
        if (targetState.getState(pos).isAir && !state.fluidState.isEmpty && state.isReplaceable) {
            result(BreakResult.Submerge(pos, state))
            return simInfo(pos, state, TargetState.Solid)?.checkPlacements() ?: true
        }

        if (breakConfig.avoidLiquids && affectsFluids()) return true

        val voxelShape = state.getOutlineShape(world, pos)

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }

        val swapStack = getSwapStack() ?: return true
        val instant = instantBreakable(
            state, pos,
            if (breakConfig.swapMode.isEnabled()) swapStack else player.mainHandStack,
            breakConfig.breakThreshold
        )

        val currentRotation = RotationManager.activeRotation
        val currentCast = currentRotation.rayCast(buildConfig.interactReach, eye)

        /* the player is buried inside the block */
        if (boxes.any { it.contains(eye) }) {
            currentCast?.blockResult?.let { blockHit ->
                val rotationRequest = RotationRequest(lookAtBlock(pos), this)
                val breakContext = BreakContext(
                    blockHit,
                    rotationRequest,
                    swapStack.inventoryIndex,
                    stackSelection,
                    instant,
                    state,
                    this
                )
                result(BreakResult.Break(pos, breakContext))
            }
            return true
        }

        val validHits = ConcurrentSet<CheckedHit>()
        val misses = ConcurrentSet<Vec3d>()
        val reachSq = buildConfig.interactReach.pow(2)

        withContext(Dispatchers.Default) {
            boxes.map { box ->
                launch {
                    val sides = if (buildConfig.checkSideVisibility) {
                        box.getVisibleSurfaces(eye).intersect(Direction.entries)
                    } else Direction.entries.toSet()
                    scanSurfaces(box, sides, buildConfig.resolution) { side, vec ->
                        if (eye distSq vec > reachSq) {
                            misses.add(vec)
                            return@scanSurfaces
                        }

                        val newRotation = eye.rotationTo(vec)

                        val hit = if (buildConfig.strictRayCast) {
                            newRotation.rayCast(buildConfig.interactReach, eye)?.blockResult
                        } else {
                            val hitVec = newRotation.castBox(box, buildConfig.interactReach, eye)
                            BlockHitResult(hitVec, side, pos, false)
                        } ?: return@scanSurfaces

                        if (hit.blockResult?.blockPos != pos) return@scanSurfaces
                        val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)

                        validHits.add(checked)
                    }
                }
            }.joinAll()
        }

        if (validHits.isEmpty()) {
            // ToDo: If we can only mine exposed surfaces we need to add not visible result here
            result(GenericResult.OutOfReach(pos, eye, misses))
            return true
        }

        val bestHit = buildConfig.pointSelection.select(validHits) ?: return true
        val blockHit = bestHit.hit.blockResult ?: return true
        val target = lookAt(bestHit.targetRotation, 0.001)
        val rotationRequest = RotationRequest(target, this)

        val breakContext = BreakContext(
            blockHit,
            rotationRequest,
            swapStack.inventoryIndex,
            stackSelection,
            instant,
            state,
            this
        )

        result(BreakResult.Break(pos, breakContext))
        return true
    }

    private fun SafeContext.getSwapStack(): ItemStack? {
        val silentSwapSelection = selectContainer {
            ofAnyType(MaterialContainer.Rank.HOTBAR)
        }

        val swapCandidates = stackSelection.containerWithMaterial(silentSwapSelection)
        if (swapCandidates.isEmpty()) {
            result(GenericResult.WrongItemSelection(pos, stackSelection, player.mainHandStack))
            return null
        }

        return swapCandidates
            .map { it.matchingStacks(stackSelection) }
            .asSequence()
            .flatten()
            .let { containerStacks ->
                var bestStack = ItemStack.EMPTY
                var bestBreakDelta = -1f
                containerStacks.forEach { stack ->
                    val breakDelta = state.calcItemBlockBreakingDelta(pos, stack)
                    if (breakDelta > bestBreakDelta ||
                        (stack == player.mainHandStack && breakDelta >= bestBreakDelta)
                    ) {
                        bestBreakDelta = breakDelta
                        bestStack = stack
                    }
                }
                bestStack
            }
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

                if (offsetState.block is Waterloggable && !fluidState.isEmpty) {
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
            val liquidOutOfBounds = affectedFluids.any { !world.worldBorder.contains(it.key) }
            if (liquidOutOfBounds) {
                result(GenericResult.Ignored(pos))
                return true
            }

            affectedFluids.forEach { (liquidPos, liquidState) ->
                result(BreakResult.Submerge(liquidPos, liquidState))
                simInfo(liquidPos, liquidState, TargetState.Solid)?.checkPlacements()
            }
            result(BreakResult.BlockedByFluid(pos, state))
            return true
        }

        return false
    }
}