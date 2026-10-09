package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.streaming.InterestTier
import com.lambda.pathing.world.streaming.PathingWorld

internal class RouteResolution(
	private val state: CoarsePlanningState,
) {
	private val planner get() = state.planner

	@Volatile
	var lastResolveReport: String = "not resolved"
		private set

	fun resolve(
		start: Stance,
		snapshotRevision: Long,
		maxExpansions: Int,
		world: PathingWorld? = null,
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? {
		val timings = PhaseTimings()
		fun extract() = timings.measure("extract") { state.routePlan(snapshotRevision, cancelled, maxExpansions) }
		fun sweep() = timings.measure("sweep") { planner.advanceReachableFrontier(from = start) }
		fun repair() = timings.measure("repair") {
			planner.repair(maxExpansions = maxExpansions, cancelled = cancelled)
		}

		var route = extract()
		if (route == null && sweep()) {
			repair()
			route = extract()
		}

		var waitRounds = 0
		var stalls = 0
		if (route == null && world != null) {
			while (route == null && !cancelled() && stalls < START_KNOWLEDGE_STALL_ROUNDS && waitRounds++ < START_KNOWLEDGE_MAX_ROUNDS) {
				state.demandCaptureLag(world)
				if (!world.awaitEvents(world.revision, START_KNOWLEDGE_WAIT_MILLIS)) stalls++
				val batch = world.drainEvents()
				timings.measure("sync") { if (!batch.isEmpty) state.applyEvents(batch) }
				val advanced = sweep()
				if (batch.isEmpty && !advanced) continue
				stalls = 0
				repair()
				route = extract()
			}
		}
		if (route == null) return report(timings, waitRounds, stalls, 0)

		val goal = state.goal
		var terminalRounds = 0
		val terminalStarted = System.nanoTime()
		while (route!!.goal != goal && terminalRounds++ < TERMINAL_ROUNDS) {
			if (cancelled()) return route
			if (world != null && terminalRounds > 1 && System.nanoTime() - terminalStarted > TERMINAL_BUDGET_MILLIS * 1_000_000L) break
			val terminal = route.goal
			world?.let { awaitTerminalKnowledge(it, terminal, timings, cancelled) }
			if (!sweep() && world == null) break
			repair()
			route = extract() ?: return report(timings, waitRounds, stalls, terminalRounds)
			if (route.goal == terminal) break
		}
		if (route.goal == goal && planner.retireAllAnchors()) {
			repair()
			route = extract() ?: route
		}
		return route.also { report(timings, waitRounds, stalls, terminalRounds) }
	}

	private fun awaitTerminalKnowledge(world: PathingWorld, terminal: Stance, timings: PhaseTimings, cancelled: () -> Boolean) {
		state.demandCaptureLag(world)
		world.interestAround(terminal, InterestTier.DEMAND)
		timings.measure("wait") {
			var waited = 0L
			while (waited < TERMINAL_KNOWLEDGE_WAIT_MILLIS && !cancelled() && world.pendingDemand > 0) {
				if (!world.awaitEvents(world.revision, DEMAND_POLL_MILLIS)) break
				waited += DEMAND_POLL_MILLIS
			}
		}
		val batch = world.drainEvents()
		timings.measure("sync") { if (!batch.isEmpty) state.applyEvents(batch) }
	}

	private fun report(timings: PhaseTimings, waitRounds: Int, stalls: Int, terminalRounds: Int): CoarseRoutePlan? {
		lastResolveReport = "wait $waitRounds rounds ($stalls stalls), terminal $terminalRounds rounds; $timings"
		return null
	}

	private companion object {

		const val TERMINAL_ROUNDS = 4

		const val TERMINAL_BUDGET_MILLIS = 1_500L

		const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L

		const val DEMAND_POLL_MILLIS = 50L

		const val START_KNOWLEDGE_WAIT_MILLIS = 200L

		const val START_KNOWLEDGE_STALL_ROUNDS = 5

		const val START_KNOWLEDGE_MAX_ROUNDS = 100
	}
}
