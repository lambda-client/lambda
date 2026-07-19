
package com.minato.interaction.construction.simulation

import com.minato.context.AutomatedSafeContext
import com.minato.interaction.construction.simulation.SimInfo.Companion.sim
import com.minato.interaction.construction.simulation.checks.BreakSim.Companion.simBreak
import com.minato.interaction.construction.simulation.checks.InteractSim.Companion.simInteraction
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.results.PostSimResult
import com.minato.util.BlockUtils.blockState
import com.minato.util.extension.Structure
import io.ktor.util.collections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import net.minecraft.util.math.Vec3d

object BuildSimulator : Sim<PostSimResult>() {
	/**
	 * Iterates over the blueprint and performs the best suited simulation. Each simulation adds [BuildResult]s to
	 * the provided concurrent set. This method uses coroutines to perform the simulations in parallel. The results
	 * will likely not be returned in the same order they were simulated due to the parallel nature of the simulations.
	 *
	 * @see SimInfo.sim
	 * @see simInteraction
	 * @see simBreak
	 */
	context(automatedSafeContext: AutomatedSafeContext)
	fun Structure.simulate(
		pov: Vec3d = automatedSafeContext.player.eyePos
	): Set<BuildResult> = runBlocking(Dispatchers.Default) {
		supervisorScope {
			val concurrentSet = ConcurrentSet<BuildResult>()

			with(automatedSafeContext) {
				forEach { (pos, targetState) ->
					launch {
						sim(
							pos,
							blockState(pos),
							targetState,
							pov,
							concurrentSet
						)
					}
				}
			}

			concurrentSet
		}
	}
}
