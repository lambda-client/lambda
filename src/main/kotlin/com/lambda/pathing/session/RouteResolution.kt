package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import kotlin.time.Duration

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
		val goal = state.goal
		val resolveStarted = System.nanoTime()
		val ledger = StringBuilder()

		var t = System.nanoTime()
		var route = state.routePlan(snapshotRevision, cancelled, maxExpansions)
		ledger.append("extract %d".format((System.nanoTime() - t) / 1_000_000L))
		if (route == null) {
			t = System.nanoTime()
			val advanced = planner.advanceReachableFrontier(from = start)
			ledger.append(", sweep %d".format((System.nanoTime() - t) / 1_000_000L))
			if (advanced) {
				t = System.nanoTime()
				planner.repair(
					timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
				)
				ledger.append(", repair %d".format((System.nanoTime() - t) / 1_000_000L))
				t = System.nanoTime()
				route = state.routePlan(snapshotRevision, cancelled, maxExpansions)
				ledger.append(", extract %d".format((System.nanoTime() - t) / 1_000_000L))
			}
		}

		if (route == null && world != null) {
			var stalls = 0
			var rounds = 0
			var extracts = 0
			var syncNanos = 0L
			var sweepNanos = 0L
			var repairNanos = 0L
			var extractNanos = 0L
			val waitStarted = System.nanoTime()
			var t: Long
			while (route == null && !cancelled() &&
				stalls < START_KNOWLEDGE_STALL_ROUNDS && rounds++ < START_KNOWLEDGE_MAX_ROUNDS
			) {
				state.demandCaptureLag(world)
				if (!world.awaitEvents(world.revision, START_KNOWLEDGE_WAIT_MILLIS)) stalls++
				val batch = world.drainEvents()
				t = System.nanoTime()
				if (!batch.isEmpty) state.applyEvents(batch)
				syncNanos += System.nanoTime() - t
				t = System.nanoTime()
				val advanced = planner.advanceReachableFrontier(from = start)
				sweepNanos += System.nanoTime() - t

				if (batch.isEmpty && !advanced) continue
				stalls = 0
				extracts++
				t = System.nanoTime()
				planner.repair(
					timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
				)
				repairNanos += System.nanoTime() - t
				t = System.nanoTime()
				route = state.routePlan(snapshotRevision, cancelled, maxExpansions)
				extractNanos += System.nanoTime() - t
			}
			ledger.append(
				"; wait %d rounds (%d extracts, %d stalls) %d ms [sync %d, sweep %d, repair %d, extract %d]".format(
					rounds, extracts, stalls, (System.nanoTime() - waitStarted) / 1_000_000L,
					syncNanos / 1_000_000L, sweepNanos / 1_000_000L, repairNanos / 1_000_000L, extractNanos / 1_000_000L,
				),
			)
		}
		if (route == null) {
			lastResolveReport = ledger.toString()
			return null
		}
		var rounds = 0
		val grantStarted = System.nanoTime()
		var grantWaitNanos = 0L
		var grantSyncNanos = 0L
		var grantSweepNanos = 0L
		var grantRepairNanos = 0L
		var grantExtractNanos = 0L
		while (route!!.goal != goal && rounds++ < TERMINAL_GRANT_ROUNDS) {
			if (cancelled()) return route

			if (world != null && rounds > 1 &&
				System.nanoTime() - grantStarted > TERMINAL_GRANT_BUDGET_MILLIS * 1_000_000L
			) break
			val terminal = route.goal

			world?.let { w ->

				state.demandCaptureLag(w)
				w.interestBlocks(
					terminal.x - TERMINAL_INTEREST_BLOCKS, terminal.y - TERMINAL_INTEREST_Y_BLOCKS,
					terminal.z - TERMINAL_INTEREST_BLOCKS,
					terminal.x + TERMINAL_INTEREST_BLOCKS, terminal.y + TERMINAL_INTEREST_Y_BLOCKS,
					terminal.z + TERMINAL_INTEREST_BLOCKS,
					InterestTier.DEMAND,
				)
				var waited = 0L
				val waitStarted = System.nanoTime()
				while (waited < TERMINAL_KNOWLEDGE_WAIT_MILLIS && !cancelled() && w.pendingDemand > 0) {
					if (!w.awaitEvents(w.revision, 50)) break
					waited += 50
				}
				grantWaitNanos += System.nanoTime() - waitStarted
				val batch = w.drainEvents()
				val syncStarted = System.nanoTime()
				if (!batch.isEmpty) state.applyEvents(batch)
				grantSyncNanos += System.nanoTime() - syncStarted
			}
			val revealed = state.grantChunksAround(terminal)
			if (revealed.isEmpty() && world == null) break
			var t = System.nanoTime()
			state.revealChunks(revealed)
			grantSyncNanos += System.nanoTime() - t
			t = System.nanoTime()
			planner.advanceReachableFrontier(from = start)
			grantSweepNanos += System.nanoTime() - t
			t = System.nanoTime()
			planner.repair(
				timeBudget = Duration.INFINITE,
				maxExpansions = maxExpansions,
				cancelled = cancelled,
			)
			grantRepairNanos += System.nanoTime() - t
			t = System.nanoTime()
			route = state.routePlan(snapshotRevision, cancelled, maxExpansions)
			grantExtractNanos += System.nanoTime() - t
			if (route == null) {
				lastResolveReport = ledger.toString()
				return null
			}
			if (route.goal == terminal) break
		}
		if (rounds > 0) {
			ledger.append(
				"; grant %d rounds %d ms [wait %d, sync %d, sweep %d, repair %d, extract %d]".format(
					rounds, (System.nanoTime() - grantStarted) / 1_000_000L,
					grantWaitNanos / 1_000_000L, grantSyncNanos / 1_000_000L, grantSweepNanos / 1_000_000L,
					grantRepairNanos / 1_000_000L, grantExtractNanos / 1_000_000L,
				),
			)
		}
		if (route.goal == goal && planner.retireAllAnchors()) {
			planner.repair(
				timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
			)

			route = state.routePlan(snapshotRevision, cancelled, maxExpansions) ?: route
		}
		ledger.append("; total %d ms".format((System.nanoTime() - resolveStarted) / 1_000_000L))
		lastResolveReport = ledger.toString()
		return route
	}

	private companion object {

		const val TERMINAL_GRANT_ROUNDS = 4

		const val TERMINAL_GRANT_BUDGET_MILLIS = 1_500L

		const val TERMINAL_INTEREST_BLOCKS = 32
		const val TERMINAL_INTEREST_Y_BLOCKS = 16

		const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L

		const val START_KNOWLEDGE_WAIT_MILLIS = 200L

		const val START_KNOWLEDGE_STALL_ROUNDS = 5

		const val START_KNOWLEDGE_MAX_ROUNDS = 100
	}
}
