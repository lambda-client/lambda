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

import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.processing.ProcessorRegistry.getProcessingInfo
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.results.PostSimResult
import com.lambda.interaction.construction.simulation.checks.BreakChecks.checkBreaks
import com.lambda.interaction.construction.simulation.checks.PlaceChecks.checkPlacements
import com.lambda.interaction.construction.simulation.checks.PostProcessingChecks.checkPostProcessing
import com.lambda.interaction.construction.simulation.checks.RequirementChecks.checkRequirements
import com.lambda.util.BlockUtils.blockState
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

object BuildSimulator : SimChecker<PostSimResult>() {
    context(context: AutomatedSafeContext)
    fun Blueprint.simulate(): Set<BuildResult> = runBlocking(Dispatchers.Default) {
        val concurrentSet = ConcurrentSet<BuildResult>()
        with(context) {
            structure.entries
                .map { (pos, targetState) ->
                    async {
                        val preProcessing = targetState.getProcessingInfo(pos) ?: return@async
                        val simInfo = SimInfo(
                            pos,
                            blockState(pos),
                            targetState,
                            preProcessing,
                            concurrentSet
                        )
                        with(simInfo) {
                            with(null) {
                                checkRequirements()
                                checkPostProcessing()
                                checkPlacements()
                                checkBreaks()
                            }
                        }
                    }
                }.awaitAll()
        }

        return@runBlocking concurrentSet
    }
}

