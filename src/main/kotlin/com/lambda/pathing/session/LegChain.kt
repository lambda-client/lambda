package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.LegHandoff
import com.lambda.pathing.search.LegSource
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.WorldEventBatch
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

internal class LegChain(
	private val world: PathingWorld?,

	private val states: List<CoarsePlanningState>,

	private val starts: List<Stance>,
	private val coarseExpansionBudget: Int,
	private val snapshotRevision: Long,
	private val cancelled: () -> Boolean,
	private val probe: SearchProbe,
	private val executor: Executor,
	private val fieldExpansionBudget: Duration,
) : LegSource {
	private class Leg(
		val state: CoarsePlanningState,
		val route: CoarseRoutePlan,
		val field: CoarseValueField,
		val resolution: RouteResolution,
		val sync: ContinuousSyncPolicy?,
	)

	private lateinit var current: Leg
	private var nextIndex = 0
	private var pending: CompletableFuture<Leg?>? = null
	private val forwarded = Array(states.size) { ConcurrentLinkedQueue<WorldEventBatch>() }

	var switches = 0
		private set

	val ledger: String get() = current.sync?.ledger ?: "coarseSync=n/a"

	fun begin(route: CoarseRoutePlan, resolution: RouteResolution): CoarseValueField {
		current = leg(0, route, resolution)
		nextIndex = 1
		prepareNext()
		return current.field
	}

	override fun hasNext(): Boolean = nextIndex < states.size

	fun sync(route: CoarseRoutePlan): WorldSyncResult = current.sync?.invoke(route) ?: WorldSyncResult.Quiet

	fun onBatch(batch: WorldEventBatch) {
		for (i in nextIndex until states.size) forwarded[i] += batch
	}

	override fun handoff(touched: Stance): LegHandoff? {
		if (!hasNext()) return null
		val index = nextIndex
		val future = pending ?: CompletableFuture.supplyAsync({ resolveLeg(index) }, executor).also { pending = it }
		val waitStarted = System.nanoTime()
		val leg = try {
			future.get(HANDOFF_WAIT_MILLIS, TimeUnit.MILLISECONDS)
		} catch (_: TimeoutException) {

			LOG.warn("Route leg {} toward {} still resolving after {} ms; finishing at {} instead", index, states[index].goal, HANDOFF_WAIT_MILLIS, touched)
			return null
		} catch (failure: RuntimeException) {
			LOG.warn("Resolving route leg {} failed", index, failure)
			null
		}
		val waited = (System.nanoTime() - waitStarted) / 1_000_000L
		pending = null
		if (leg == null) {
			LOG.warn("Route leg {} toward {} did not resolve; finishing at {} instead", index, states[index].goal, touched)
			nextIndex = states.size
			return null
		}

		var changed = false
		while (true) {
			val batch = forwarded[index].poll() ?: break
			if (batch.isEmpty) continue
			leg.state.applyEvents(batch)
			changed = true
		}
		val route = if (changed) {
			leg.resolution.resolve(starts[index], snapshotRevision, coarseExpansionBudget, cancelled = cancelled) ?: leg.route
		} else leg.route

		current = leg
		nextIndex = index + 1
		switches++
		LOG.info(
			"Leg handoff at {}: next goal {}, route {} nodes, waited {} ms",
			touched, leg.state.goal, route.nodes.size, waited,
		)
		prepareNext()
		return LegHandoff(route, leg.field, touched)
	}

	private fun prepareNext() {
		if (!hasNext() || pending != null) return
		val index = nextIndex
		pending = CompletableFuture.supplyAsync({ resolveLeg(index) }, executor)
	}

	private fun resolveLeg(index: Int): Leg? {
		val state = states[index]
		val start = starts[index]
		forwarded[index].clear()
		val started = System.nanoTime()
		state.repairFrom(start)
		val planner = state.planner
		val coarse = planner.repair(timeBudget = Duration.INFINITE, maxExpansions = coarseExpansionBudget, cancelled = cancelled)
		if (!coarse.converged || coarse.cancelled || cancelled()) {
			if (!coarse.converged && !coarse.cancelled) LOG.warn("Route leg {} coarse search did not converge ({} expansions){}", index, coarse.processedNodes, coarse.stall?.let { "; stalled: $it" } ?: "")
			return null
		}
		planner.expandSearchField(fieldExpansionBudget, cancelled)
		val resolution = RouteResolution(state)

		val deadline = started + LEG_RESOLVE_BUDGET_MILLIS * 1_000_000L
		var rounds = 0
		var route: CoarseRoutePlan?
		while (true) {
			route = resolution.resolve(start, snapshotRevision, coarseExpansionBudget, cancelled = cancelled)
			if (route != null || world == null || cancelled() || System.nanoTime() > deadline) break
			rounds++
			state.demandCaptureLag(world)
			if (rounds == 1) InterestPrimer.primeJourney(world, start, state.goal)
			world.awaitEvents(world.revision, LEG_RESOLVE_WAIT_MILLIS)
			while (true) {
				val batch = forwarded[index].poll() ?: break
				if (!batch.isEmpty) state.applyEvents(batch)
			}
		}
		if (route == null) {
			LOG.warn(
				"Route leg {} ahead: no coarse route {} -> {} after {} capture rounds ({} ms): {}",
				index, start, state.goal, rounds, (System.nanoTime() - started) / 1_000_000L, planner.routeFailureReport(),
			)
			return null
		}
		LOG.info(
			"Resolved route leg {} ahead: {} -> {}, {} expansions, {} route nodes, {} capture rounds, {} ms",
			index, start, state.goal, coarse.processedNodes, route.nodes.size, rounds,
			(System.nanoTime() - started) / 1_000_000L,
		)
		return leg(index, route, resolution)
	}

	private fun leg(index: Int, route: CoarseRoutePlan, resolution: RouteResolution): Leg {
		val state = states[index]
		val field = state.planner.valueField()
		val sync = world?.let {
			ContinuousSyncPolicy(
				world = it,
				coarseState = state,
				resolution = resolution,
				field = field,
				start = starts[index],
				snapshotRevision = snapshotRevision,
				cancelled = cancelled,
				probe = probe,
				onBatch = ::onBatch,
			)
		}
		return Leg(state, route, field, resolution, sync)
	}

	private companion object {

		const val HANDOFF_WAIT_MILLIS = 4_000L

		const val LEG_RESOLVE_BUDGET_MILLIS = 30_000L

		const val LEG_RESOLVE_WAIT_MILLIS = 250L
	}
}
