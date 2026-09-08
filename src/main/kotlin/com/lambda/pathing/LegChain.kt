package com.lambda.pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.LegHandoff
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.session.ContinuousSyncPolicy
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.world.InterestPrimer
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.WorldEventBatch
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import kotlin.time.Duration

/**
 * The legs of a compound route as one running search sees them. The current leg owns the
 * coarse planner the search steers by; the next leg is resolved ahead on [executor]
 * (its own D* from the waypoint it starts at, field expansion, route) while the body is
 * still on the current one, so a walk-through handoff costs the search nothing but a
 * re-root. World events drained by the current leg's sync policy are queued for the legs
 * behind it and applied when each becomes current. See docs/decisions/arrival.md.
 */
private val LOG = LogManager.getLogger(com.lambda.Lambda.SYMBOL)

internal class LegChain(
	private val world: PathingWorld?,
	/** The legs after the first: their coarse states, in walking order. */
	private val states: List<CoarsePlanningState>,
	/** Where each of [states] starts: the goal of the leg before it. */
	private val starts: List<Stance>,
	private val coarseExpansionBudget: Int,
	private val snapshotRevision: Long,
	private val cancelled: () -> Boolean,
	private val probe: SearchProbe,
	private val executor: Executor,
	private val fieldExpansionTicks: Double,
	private val fieldExpansionBudget: Duration,
	private val fieldExpansionNodes: Int,
) {
	class Leg(
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

	/** Wall time the search spent waiting on a leg that was not resolved yet, summed. */
	var waitedMillis = 0L
		private set

	val planner: CoarsePlanner get() = current.state.planner

	val ledger: String get() = current.sync?.ledger ?: "coarseSync=n/a"

	fun begin(first: Leg) {
		current = first
		prepareNext()
	}

	fun hasNext(): Boolean = nextIndex < states.size

	fun sync(route: CoarseRoutePlan): WorldSyncResult = current.sync?.invoke(route) ?: WorldSyncResult.Quiet

	fun expandGuide(marginTicks: Double) {
		planner.expandField(
			extraTicks = fieldExpansionTicks + marginTicks,
			timeBudget = fieldExpansionBudget,
			maxExpansions = fieldExpansionNodes,
		)
	}

	/** Every batch the current leg drains is queued for the legs still ahead. */
	fun onBatch(batch: WorldEventBatch) {
		for (i in nextIndex until states.size) forwarded[i] += batch
	}

	/**
	 * The search touched the current leg's goal: hand it the next leg. Blocks while that
	 * leg is still resolving (the body has the published runway meanwhile). Null when the
	 * next leg could not be resolved; the search then finishes at this goal instead.
	 */
	fun handoff(touched: Stance): LegHandoff? {
		if (!hasNext()) return null
		val index = nextIndex
		val future = pending ?: CompletableFuture.supplyAsync({ resolveLeg(index) }, executor).also { pending = it }
		val waitStarted = System.nanoTime()
		val leg = try {
			future.get(HANDOFF_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
		} catch (_: java.util.concurrent.TimeoutException) {
			// Still resolving: the search finishes this leg at the waypoint and the walk
			// continues from rest with the remaining waypoints. Not a failure of the leg.
			LOG.warn("Route leg {} toward {} still resolving after {} ms; finishing at {} instead", index + 1, states[index].goal, HANDOFF_WAIT_MILLIS, touched)
			waitedMillis += (System.nanoTime() - waitStarted) / 1_000_000L
			return null
		} catch (failure: RuntimeException) {
			LOG.warn("Resolving route leg {} failed", index + 1, failure)
			null
		}
		val waited = (System.nanoTime() - waitStarted) / 1_000_000L
		waitedMillis += waited
		pending = null
		if (leg == null) {
			LOG.warn("Route leg {} toward {} did not resolve; finishing at {} instead", index + 1, states[index].goal, touched)
			nextIndex = states.size
			return null
		}

		// World events that landed while this leg was being resolved.
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

	/**
	 * One leg's coarse work, off the search thread: D* from its start, the guide field,
	 * the route. Resolved against the snapshot as it is now (the leg's event queue is
	 * cleared first; anything that lands during the work is applied at the handoff). No
	 * world waits: a route cut at the ring is extended by the leg's own sync policy later.
	 */
	private fun resolveLeg(index: Int): Leg? {
		val state = states[index]
		val start = starts[index]
		forwarded[index].clear()
		val started = System.nanoTime()
		state.repairFrom(start, emptySet(), emptySet())
		val planner = state.planner
		val coarse = planner.repair(timeBudget = Duration.INFINITE, maxExpansions = coarseExpansionBudget, cancelled = cancelled)
		if (!coarse.converged || coarse.cancelled || cancelled()) {
			if (!coarse.converged && !coarse.cancelled) LOG.warn("Route leg {} coarse search did not converge ({} expansions){}", index + 1, coarse.processedNodes, coarse.stall?.let { "; stalled: $it" } ?: "")
			return null
		}
		planner.expandField(
			extraTicks = fieldExpansionTicks,
			timeBudget = fieldExpansionBudget,
			maxExpansions = fieldExpansionNodes,
			cancelled = cancelled,
		)
		val resolution = RouteResolution(state)
		// No route yet usually means the leg's terrain is not captured: demand it, wait for
		// capture, fold in what the current leg's policy drained meanwhile, try again.
		// Never drains the world itself; only the current leg's policy may.
		val deadline = started + LEG_RESOLVE_BUDGET_MILLIS * 1_000_000L
		var rounds = 0
		var route: CoarseRoutePlan? = null
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
				index + 1, start, state.goal, rounds, (System.nanoTime() - started) / 1_000_000L, planner.routeFailureReport(),
			)
			return null
		}
		val field = planner.valueField()
		val sync = world?.let {
			ContinuousSyncPolicy(
				world = it,
				coarseState = state,
				resolution = resolution,
				field = field,
				start = start,
				finalGoal = state.goal,
				snapshotRevision = snapshotRevision,
				coarseExpansionBudget = coarseExpansionBudget,
				cancelled = cancelled,
				probe = probe,
				onBatch = ::onBatch,
			)
		}
		LOG.info(
			"Resolved route leg {} ahead: {} -> {}, {} expansions, {} route nodes, {} capture rounds, {} ms",
			index + 1, start, state.goal, coarse.processedNodes, route.nodes.size, rounds,
			(System.nanoTime() - started) / 1_000_000L,
		)
		return Leg(state, route, field, resolution, sync)
	}

	private companion object {
		/** How long the search waits at a touch for a leg still resolving before finishing there. */
		const val HANDOFF_WAIT_MILLIS = 4_000L

		/** Wall budget for resolving one leg ahead, capture waits included. */
		const val LEG_RESOLVE_BUDGET_MILLIS = 30_000L

		const val LEG_RESOLVE_WAIT_MILLIS = 250L
	}
}
