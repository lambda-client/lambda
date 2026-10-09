package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import kotlin.math.abs

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
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? = resolving(snapshotRevision, maxExpansions, cancelled) {
		extract() ?: if (sweep(start)) extract() else null
	}

	fun extend(
		route: CoarseRoutePlan,
		start: Stance,
		snapshotRevision: Long,
		maxExpansions: Int,
		cancelled: () -> Boolean = { false },
	): CoarseRoutePlan? {
		if (route.goal == state.goal || !within(route.goal, start, SWEEP_REACH_BLOCKS)) return null
		return resolving(snapshotRevision, maxExpansions, cancelled) { if (sweep(start)) extract() else null }
	}

	private inner class Pass(
		private val snapshotRevision: Long,
		private val maxExpansions: Int,
		private val cancelled: () -> Boolean,
	) {
		val timings = PhaseTimings()

		fun extract(): CoarseRoutePlan? = timings.measure("extract") { state.routePlan(snapshotRevision, cancelled, maxExpansions) }

		fun sweep(start: Stance): Boolean {
			if (cancelled() || !timings.measure("sweep") { planner.advanceReachableFrontier(from = start) }) return false
			repair()
			return true
		}

		fun repair() {
			timings.measure("repair") { planner.repair(maxExpansions = maxExpansions, cancelled = cancelled) }
		}
	}

	private inline fun resolving(
		snapshotRevision: Long,
		maxExpansions: Int,
		noinline cancelled: () -> Boolean,
		block: Pass.() -> CoarseRoutePlan?,
	): CoarseRoutePlan? {
		val pass = Pass(snapshotRevision, maxExpansions, cancelled)
		var route = pass.block()
		if (route != null && route.goal == state.goal && planner.retireAllAnchors()) {
			pass.repair()
			route = pass.extract() ?: route
		}
		lastResolveReport = pass.timings.toString()
		return route
	}

	private fun within(a: Stance, b: Stance, blocks: Int): Boolean = abs(a.x - b.x) <= blocks && abs(a.z - b.z) <= blocks

	private companion object {
		const val SWEEP_REACH_BLOCKS = 96
	}
}
