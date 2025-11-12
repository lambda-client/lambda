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

package com.lambda.interaction.construction.simulation

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.construction.processing.PreProcessingInfo
import com.lambda.interaction.construction.processing.ProcessorRegistry.getProcessingInfo
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.simulation.checks.BasicChecker.hasBasicRequirements
import com.lambda.interaction.construction.verify.TargetState
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.*

/**
 * An interface representing all the information required to simulate a state. All simulators must present their public api
 * as an extension of the [SimInfo] class to allow easy access through the DSL style sim builder.
 */
interface ISimInfo : Automated {
    val pos: BlockPos
    val state: BlockState
    val targetState: TargetState
    val preProcessing: PreProcessingInfo
    val pov: Vec3d
    val concurrentResults: MutableSet<BuildResult>
    val dependencyStack: Stack<Sim<*>>

    companion object {
        /**
         * Creates a [SimInfo], checks its basic requirements, and runs the [simBuilder] block.
         */
        @SimDsl
        context(_: BuildSimulator)
        suspend fun AutomatedSafeContext.sim(
            pos: BlockPos,
            state: BlockState,
            targetState: TargetState,
            pov: Vec3d,
            concurrentResults: MutableSet<BuildResult>,
            simBuilder: suspend SimInfo.() -> Unit
        ) {
            SimInfo(
                pos, state, targetState,
                targetState.getProcessingInfo(pos) ?: return,
                pov, Stack(), concurrentResults, this
            ).takeIf { it.hasBasicRequirements() }?.run { simBuilder() }
        }

        /**
         * Creates a new [SimInfo] using the current [ISimInfo]'s [dependencyStack] and [concurrentResults],
         * checks its basic requirements, and runs the [simBuilder] block. As simulations tend to make use of
         * concurrency, a new stack is created and the dependencies from the previous stack are added.
         */
        @SimDsl
        context(_: AutomatedSafeContext)
        suspend fun ISimInfo.sim(
            pos: BlockPos = this.pos,
            state: BlockState = this.state,
            targetState: TargetState = this.targetState,
            pov: Vec3d = this.pov,
            simBuilder: suspend SimInfo.() -> Unit
        ) {
            SimInfo(
                pos, state, targetState,
                targetState.getProcessingInfo(pos) ?: return,
                pov, Stack<Sim<*>>().apply { addAll(dependencyStack) }, concurrentResults, this
            ).takeIf { it.hasBasicRequirements() }?.run { simBuilder() }
        }
    }
}

data class SimInfo(
    override val pos: BlockPos,
    override val state: BlockState,
    override val targetState: TargetState,
    override val preProcessing: PreProcessingInfo,
    override val pov: Vec3d,
    override val dependencyStack: Stack<Sim<*>>,
    override val concurrentResults: MutableSet<BuildResult>,
    private val automated: Automated
) : ISimInfo, Automated by automated