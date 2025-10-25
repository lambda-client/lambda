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
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.InteractResult
import com.lambda.interaction.construction.simulation.ISimInfo
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimCheckerDsl
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.util.math.distSq
import com.lambda.util.math.vec3d
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.state.property.Properties
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

class PostProcessingChecker @SimCheckerDsl private constructor(simInfo: SimInfo)
    : SimChecker<InteractResult>(), Dependable,
    ISimInfo by simInfo
{
    override fun asDependent(buildResult: BuildResult) =
        InteractResult.Dependency(pos, buildResult)

    companion object {
        @SimCheckerDsl
        context(automatedSafeContext: AutomatedSafeContext, dependable: Dependable?)
        fun SimInfo.checkPostProcessing() =
            PostProcessingChecker(this).run {
                checkDependent(dependable)
                automatedSafeContext.checkPostProcessing()
            }
    }

    private fun AutomatedSafeContext.checkPostProcessing(): Boolean {
        val targetState = (targetState as? TargetState.State) ?: return false

        if (!targetState.matches(state, pos, preProcessing.ignore)) return false

        val mismatchedProperties = state.properties.filter { state.get(it) != targetState.blockState.get(it) }
        mismatchedProperties.forEach { property ->
            when (property) {
                Properties.EYE -> {
                    if (state.get(Properties.EYE)) return@forEach
                    val expectedState = state.with(Properties.EYE, true)
                    simInteraction(expectedState)
                }

                Properties.INVERTED -> {
                    val expectedState = state.with(Properties.INVERTED, !state.get(Properties.INVERTED))
                    simInteraction(expectedState)
                }

                Properties.DELAY -> {
                    val expectedState =
                        state.with(Properties.DELAY, state.cycle(Properties.DELAY).get(Properties.DELAY))
                    simInteraction(expectedState)
                }

                Properties.COMPARATOR_MODE -> {
                    val expectedState = state.with(
                        Properties.COMPARATOR_MODE,
                        state.cycle(Properties.COMPARATOR_MODE).get(Properties.COMPARATOR_MODE)
                    )
                    simInteraction(expectedState)
                }

                Properties.OPEN -> {
                    val expectedState = state.with(Properties.OPEN, !state.get(Properties.OPEN))
                    simInteraction(expectedState)
                }

                Properties.SLAB_TYPE -> return false
            }
        }

        return true
    }

    private fun AutomatedSafeContext.simInteraction(
        expectedState: BlockState,
        sides: Set<Direction>? = null,
        item: Item? = null,
        placing: Boolean = false
    ) {
        val boxes = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
        val validHits = mutableListOf<CheckedHit>()
        val blockedHits = mutableSetOf<Vec3d>()
        val misses = mutableSetOf<Vec3d>()
        val airPlace = placing && placeConfig.airPlace.isEnabled

        boxes.forEach { box ->
            val refinedSides = if (buildConfig.checkSideVisibility) {
                box.getVisibleSurfaces(pov).let { visibleSides ->
                    sides?.let { specific ->
                        visibleSides.intersect(specific)
                    } ?: visibleSides.toSet()
                }
            } else sides ?: Direction.entries.toSet()

            scanSurfaces(
                box,
                refinedSides,
                buildConfig.resolution,
                preProcessing.surfaceScan
            ) { hitSide, vec ->
                val distSquared = pov distSq vec
                if (distSquared > buildConfig.interactReach.pow(2)) {
                    misses.add(vec)
                    return@scanSurfaces
                }

                val newRotation = pov.rotationTo(vec)

                val hit = if (buildConfig.strictRayCast) {
                    val rayCast = newRotation.rayCast(buildConfig.interactReach, pov)
                    when {
                        rayCast != null && (!airPlace || pov distSq rayCast.pos <= distSquared) ->
                            rayCast.blockResult

                        airPlace -> {
                            val hitVec = newRotation.castBox(box, buildConfig.interactReach, pov)
                            BlockHitResult(hitVec, hitSide, pos, false)
                        }

                        else -> null
                    }
                } else {
                    val hitVec = newRotation.castBox(box, buildConfig.interactReach, pov)
                    BlockHitResult(hitVec, hitSide, pos, false)
                } ?: return@scanSurfaces

                val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)
                if (hit.blockResult?.blockPos != pos) {
                    blockedHits.add(vec)
                    return@scanSurfaces
                }

                validHits.add(checked)
            }
        }

        if (validHits.isEmpty()) {
            if (misses.isNotEmpty()) {
                result(GenericResult.OutOfReach(pos, pov, misses))
                return
            }

            //ToDo: Must clean up surface scan usage / renders. Added temporary direction until changes are made
            result(GenericResult.NotVisible(pos, pos, pov.distanceTo(pos.vec3d)))
            return
        }

        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            val checkedResult = checkedHit.hit.blockResult ?: return
            val rotationTarget = lookAt(checkedHit.targetRotation, 0.001)
            val context = InteractionContext(
                checkedResult,
                RotationRequest(rotationTarget, this),
                player.inventory.selectedSlot,
                state,
                expectedState,
                this
            )

            val stackSelection = (item ?: player.mainHandStack.item).select()
            val hotbarCandidates = selectContainer {
                matches(stackSelection) and ofAnyType(MaterialContainer.Rank.HOTBAR)
            }.let { predicate ->
                stackSelection.containerWithMaterial( predicate)
            }

            if (hotbarCandidates.isEmpty()) {
                result(
                    GenericResult.WrongItemSelection(
                        pos,
                        stackSelection,
                        player.mainHandStack
                    )
                )
                return
            } else {
                context.hotbarIndex =
                    player.hotbar.indexOf(hotbarCandidates.first().matchingStacks(stackSelection).first())
            }

            result(InteractResult.Interact(pos, context))
        }
    }
}