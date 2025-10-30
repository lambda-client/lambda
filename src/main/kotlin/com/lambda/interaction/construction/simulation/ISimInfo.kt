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
import com.lambda.interaction.construction.result.Dependable
import com.lambda.interaction.construction.simulation.checks.RequirementChecker.checkRequirements
import com.lambda.interaction.construction.verify.TargetState
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.*

@DslMarker
annotation class SimBuilderDsl

interface ISimInfo : Automated {
    val pos: BlockPos
    val state: BlockState
    val targetState: TargetState
    val preProcessing: PreProcessingInfo
    val pov: Vec3d
    val concurrentResults: MutableSet<BuildResult>
    val dependencyStack: Stack<Dependable>

    companion object {
        @SimBuilderDsl
        private suspend fun ISimInfo.sim(
            dependable: Dependable?,
            simBuilder: suspend context(Dependable?) SimBuilder.() -> Unit
        ) = SimBuilder(this).run { with(dependable) { simBuilder() } }

        @SimBuilderDsl
        suspend fun AutomatedSafeContext.sim(
            pos: BlockPos,
            state: BlockState,
            targetState: TargetState,
            pov: Vec3d,
            concurrentResults: MutableSet<BuildResult>,
            simBuilder: suspend context(Dependable?) SimBuilder.() -> Unit
        ) {
            SimInfo(
                pos, state, targetState,
                targetState.getProcessingInfo(pos) ?: return,
                pov, concurrentResults, Stack(), this
            ).takeIf { it.checkRequirements() }?.sim(null, simBuilder)
        }

        @SimBuilderDsl
        context(_: AutomatedSafeContext, dependable: Dependable)
        suspend fun ISimInfo.sim(
            pos: BlockPos = this.pos,
            state: BlockState = this.state,
            targetState: TargetState = this.targetState,
            pov: Vec3d = this.pov,
            simBuilder: suspend context(Dependable?) SimBuilder.() -> Unit
        ) {
            SimInfo(
                pos, state, targetState,
                targetState.getProcessingInfo(pos) ?: return,
                pov, concurrentResults, dependencyStack, this
            ).takeIf { it.checkRequirements() }?.sim(dependable, simBuilder)
        }

        @SimBuilderDsl
        context(dependable: Dependable)
        suspend fun ISimInfo.sim(
            simBuilder: suspend context(Dependable?) SimBuilder.() -> Unit
        ) = sim(dependable, simBuilder)
    }
}

@SimBuilderDsl
data class SimInfo(
    override val pos: BlockPos,
    override val state: BlockState,
    override val targetState: TargetState,
    override val preProcessing: PreProcessingInfo,
    override val pov: Vec3d,
    override val concurrentResults: MutableSet<BuildResult>,
    override val dependencyStack: Stack<Dependable>,
    val automated: Automated
) : ISimInfo, Automated by automated {

}

class SimBuilder(simInfo: ISimInfo) : ISimInfo by simInfo