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
import com.lambda.interaction.construction.context.InteractContext
import com.lambda.interaction.construction.processing.ProcessorRegistry.intermediaryBlockMap
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.InteractResult
import com.lambda.interaction.construction.simulation.ISimInfo
import com.lambda.interaction.construction.simulation.ISimInfo.Companion.sim
import com.lambda.interaction.construction.simulation.Sim
import com.lambda.interaction.construction.simulation.SimDsl
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.simulation.checks.PlaceSim.Companion.simPlacement
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.util.item.ItemStackUtils.inventoryIndex
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction

class PostProcessingSim private constructor(simInfo: ISimInfo)
    : Sim<InteractResult>(),
    ISimInfo by simInfo
{
    override fun dependentUpon(buildResult: BuildResult) =
        InteractResult.Dependency(pos, buildResult)

    companion object {
        context(automatedSafeContext: AutomatedSafeContext, dependent: Sim<*>)
        @SimDsl
        suspend fun SimInfo.simPostProcessing() =
            PostProcessingSim(this).run {
                withDependent(dependent) {
                    automatedSafeContext.simPostProcessing()
                }
            }
    }

    private suspend fun AutomatedSafeContext.simPostProcessing() {
        val targetState = (targetState as? TargetState.State) ?: return

        intermediaryBlockMap[targetState.getState(pos).block]?.let { intermediaryInfo ->
            intermediaryInfo.getIntermediaryProcess(state)?.let { intermediaryBlock ->
                simInteraction(
                    intermediaryBlock.targetBlock.defaultState,
                    intermediaryBlock.sides,
                    intermediaryBlock.item
                )
            }
            return
        }

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

                Properties.SLAB_TYPE -> sim { simPlacement() }
            }
        }
    }

    private suspend fun AutomatedSafeContext.simInteraction(
        expectedState: BlockState,
        sides: Set<Direction> = Direction.entries.toSet(),
        item: Item? = null
    ) {
        val validHits = scanShape(pov, state.getOutlineShape(world, pos), pos, sides, preProcessing)
            ?: return

        val swapStack = getSwapStack(item ?: player.mainHandStack.item) ?: return

        selectHit(validHits, expectedState, swapStack)
    }

    private fun AutomatedSafeContext.getSwapStack(item: Item): ItemStack? {
        val stackSelection = item.select()
        val hotbarCandidates = selectContainer {
            ofAnyType(MaterialContainer.Rank.Hotbar)
        }.let { predicate ->
            stackSelection.containerWithMaterial( predicate)
        }

        if (hotbarCandidates.isEmpty()) {
            result(GenericResult.WrongItemSelection(pos, stackSelection, player.mainHandStack))
            return null
        }

        return hotbarCandidates.first().matchingStacks(stackSelection).first()
    }

    private fun AutomatedSafeContext.selectHit(
        validHits: Collection<CheckedHit>,
        expectedState: BlockState,
        swapStack: ItemStack
    ) {
        buildConfig.pointSelection.select(validHits)?.let { checkedHit ->
            val checkedResult = checkedHit.hit.blockResult ?: return
            val rotationTarget = lookAt(checkedHit.targetRotation, 0.001)
            val context = InteractContext(
                checkedResult,
                RotationRequest(rotationTarget, this),
                swapStack.inventoryIndex,
                state,
                expectedState,
                this
            )

            result(InteractResult.Interact(pos, context))
        }
    }
}