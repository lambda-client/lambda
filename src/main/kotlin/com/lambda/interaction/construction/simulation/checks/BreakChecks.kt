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
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.result.results.BreakResult
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.simulation.checks.PlaceChecks.checkPlacements
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection
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
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.math.distSq
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.player.gamemode
import com.lambda.util.world.raycast.RayCastUtils.blockResult
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
import kotlin.math.pow

object BreakChecks : SimChecker<BreakResult>(), Dependable {
    override fun SimInfo.asDependant(buildResult: BuildResult) =
        BreakResult.Dependency(pos, buildResult)

    context(automatedSafeContext: AutomatedSafeContext, dependable: Dependable?)
    fun SimInfo.checkBreaks(): Unit = with(automatedSafeContext) {
        checkDependant(dependable)

        /* is a block that will be destroyed by breaking adjacent blocks */
        if (!breakConfig.breakWeakBlocks && state.block.hardness == 0f && !state.isAir && state.isNotEmpty) {
            result(GenericResult.Ignored(pos))
            return
        }

        /* player is standing on top of the block */
        if (breakConfig.avoidSupporting) player.supportingBlockPos.orElse(null)?.let { support ->
            if (support != pos) return@let
            result(BreakResult.PlayerOnTop(pos, state))
            return
        }

        /* liquid needs to be submerged first to be broken */
        if (!state.fluidState.isEmpty && state.isReplaceable) {
            result(BreakResult.Submerge(pos, state))
            this@BreakChecks.run { checkPlacements() }
            return
        }

        if (breakConfig.avoidLiquids) {
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
                    return
                }

                affectedFluids.forEach { (liquidPos, liquidState) ->
                    result(BreakResult.Submerge(liquidPos, liquidState))
                    this@BreakChecks.run { checkPlacements(liquidPos, liquidState, TargetState.Solid) }
                }
                result(BreakResult.BlockedByFluid(pos, state))
                return
            }
        }

        val currentRotation = RotationManager.activeRotation
        val currentCast = currentRotation.rayCast(buildConfig.interactReach, eye)

        val voxelShape = state.getOutlineShape(world, pos)
        voxelShape.getClosestPointTo(eye).ifPresent {
            // ToDo: Use closest point of shape of only visible faces
        }

        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }
        val verify: CheckedHit.() -> Boolean = {
            hit.blockResult?.blockPos == pos
        }

        // ToDo: Move this to a location where more of the context parameters can be properly set
        /* the player is buried inside the block */
        if (boxes.any { it.contains(eye) }) {
            currentCast?.blockResult?.let { blockHit ->
                val rotationRequest = RotationRequest(
                    lookAtBlock(pos), this
                )
                val breakContext = BreakContext(
                    blockHit,
                    rotationRequest,
                    player.inventory.selectedSlot,
                    StackSelection.EVERYTHING.select(),
                    instantBreakable(state, pos, breakConfig.breakThreshold),
                    state,
                    breakConfig.sorter,
                    this
                )
                result(BreakResult.Break(pos, breakContext))
                return
            }
        }

        val validHits = mutableListOf<CheckedHit>()
        val misses = mutableSetOf<Vec3d>()
        val reachSq = buildConfig.interactReach.pow(2)

        boxes.forEach { box ->
            val sides = if (buildConfig.checkSideVisibility) {
                box.getVisibleSurfaces(eye).intersect(Direction.entries)
            } else Direction.entries.toSet()
            // ToDo: Rewrite Rotation request system to allow support for all sim features and use the rotation finder
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

                val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)
                if (!checked.verify()) return@scanSurfaces

                validHits.add(checked)
            }
        }

        if (validHits.isEmpty()) {
            // ToDo: If we can only mine exposed surfaces we need to add not visible result here
            result(GenericResult.OutOfReach(pos, eye, misses))
            return
        }

        val bestHit = buildConfig.pointSelection.select(validHits) ?: return
        val blockHit = bestHit.hit.blockResult ?: return
        val target = lookAt(bestHit.targetRotation, 0.001)
        val rotationRequest = RotationRequest(target, this)
        val instant = instantBreakable(state, pos, breakConfig.breakThreshold)

        val breakContext = BreakContext(
            blockHit,
            rotationRequest,
            player.inventory.selectedSlot,
            StackSelection.EVERYTHING.select(),
            instant,
            state,
            breakConfig.sorter,
            this
        )

        if (gamemode.isCreative) {
            result(BreakResult.Break(pos, breakContext))
            return
        }

        val stackSelection = selectStack(
            sorter = compareByDescending<ItemStack> {
                it.canBreak(CachedBlockPosition(world, pos, false))
            }.thenByDescending {
                state.calcItemBlockBreakingDelta(pos, it)
            }
        ) {
            isTool() and if (breakConfig.suitableToolsOnly) {
                isSuitableForBreaking(state)
            } else any() and if (breakConfig.forceSilkTouch) {
                hasEnchantment(Enchantments.SILK_TOUCH)
            } else any() and if (breakConfig.forceFortunePickaxe) {
                hasEnchantment(Enchantments.FORTUNE)
            } else any() and if (!breakConfig.useWoodenTools) {
                hasTag(WOODEN_TOOL_MATERIALS).not()
            } else any() and if (!breakConfig.useStoneTools) {
                hasTag(STONE_TOOL_MATERIALS).not()
            } else any() and if (!breakConfig.useIronTools) {
                hasTag(IRON_TOOL_MATERIALS).not()
            } else any() and if (!breakConfig.useDiamondTools) {
                hasTag(DIAMOND_TOOL_MATERIALS).not()
            } else any() and if (!breakConfig.useGoldTools) {
                hasTag(GOLD_TOOL_MATERIALS).not()
            } else any() and if (!breakConfig.useNetheriteTools) {
                hasTag(NETHERITE_TOOL_MATERIALS).not()
            } else any()
        }

        val silentSwapSelection = selectContainer {
            ofAnyType(MaterialContainer.Rank.HOTBAR)
        }

        val swapCandidates = stackSelection.containerWithMaterial(silentSwapSelection)
        if (swapCandidates.isEmpty()) {
            result(GenericResult.WrongItemSelection(pos, breakContext, stackSelection, player.mainHandStack))
            return
        }

        val swapStack = swapCandidates
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

        breakContext.apply {
            hotbarIndex = player.hotbar.indexOf(swapStack)
            itemSelection = stackSelection
            instantBreak = instantBreakable(
                state,
                pos,
                if (breakConfig.swapMode.isEnabled()) swapStack else player.mainHandStack,
                breakConfig.breakThreshold
            )
        }
        result(BreakResult.Break(pos, breakContext))
        return
    }
}