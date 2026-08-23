/*
 * Copyright 2026 Lambda
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
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.results.PostSimResult
import com.lambda.interaction.construction.simulation.sims.simBreak
import com.lambda.interaction.construction.simulation.sims.simInteraction
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.Structure
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import net.minecraft.util.math.Vec3d

/**
 * Iterates over the blueprint and performs the best suited simulation. Each simulation adds [BuildResult]s to
 * the provided concurrent set. This method uses coroutines to perform the simulations in parallel. The results
 * will likely not be returned in the same order they were simulated due to the parallel nature of the simulations.
 *
 * @see SimInfo.sim
 * @see simInteraction
 * @see simBreak
 */
@SimDsl
context(automatedSafeContext: AutomatedSafeContext)
fun Structure.sim(
	pov: Vec3d = automatedSafeContext.player.eyePos
) = BuildSimulator.sim(this, pov)

class BuildSimulator private constructor(
	private val structure: Structure,
	private val pov: Vec3d,
	private val resultsCollection: MutableSet<BuildResult>
) : Sim<PostSimResult>() {
	override suspend fun AutomatedSafeContext.sim() {
		supervisorScope {
			structure.forEach { (pos, targetState) ->
				launch {
					sim(
						pos,
						blockState(pos),
						targetState,
						pov,
						resultsCollection
					)
				}
			}
		}
	}

	companion object {
		@SimDsl
		context(automatedSafeContext: AutomatedSafeContext)
		internal fun sim(
			structure: Structure,
			pov: Vec3d = automatedSafeContext.player.eyePos
		): Set<BuildResult> =
			with(automatedSafeContext) {
				runBlocking(Dispatchers.Default) {
					val results = ConcurrentSet<BuildResult>()
					with(BuildSimulator(structure, pov, results)) { sim() }
					results
				}
			}
	}
}
