package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.world.streaming.PathingWorld
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

internal class PlanningJob(
	private val preparation: PlanningPreparation,
	private val world: PathingWorld,
	private val legStates: List<CoarsePlanningState>,
	private val cancelled: () -> Boolean,
	private val planningGeneration: Long,
	private val snapshotRevision: Long,
	private val execution: ExecutionFeedback,
	private val publish: (PublishedPath, Boolean) -> Unit,
) {
	private val coarseState = legStates.first()
	private val planner = coarseState.planner
	private val snapshot = world.snapshot
	private val start = preparation.start
	private val goal = coarseState.goal
	private val resolution = RouteResolution(coarseState)
	private val timings = PhaseTimings()

	fun start(): CompletableFuture<PathPlanResult> = CompletableFuture.supplyAsync(::run, PLANNER)

	private fun run(): PathPlanResult = if (cancelled()) PathPlanResult.Cancelled else plan()

	private fun plan(): PathPlanResult {
		timings.measure("knowledge-wait") { awaitStartKnowledge() }
		val batch = world.drainEvents()
		coarseState.repairFrom(start)
		coarseState.applyEvents(batch)
		val coarse = timings.measure("coarse") {
			planner.repair(maxExpansions = preparation.coarseExpansionBudget, cancelled = cancelled)
		}
		LOG.info(
			"Coarse D* {} -> {}: {} expansions, {} graph nodes, {} snapshot sections, converged={}",
			start, goal, coarse.processedNodes, planner.graphSize, snapshot.sectionCount, coarse.converged,
		)
		if (coarse.cancelled || cancelled()) return PathPlanResult.Cancelled
		if (!coarse.converged) {
			return PathPlanResult.Failed(
				"coarse search did not converge within ${preparation.coarseExpansionBudget} expansions" +
						(coarse.stall?.let { "; stalled: $it" } ?: "")
			)
		}
		timings.measure("field") { planner.expandSearchField(FIELD_EXPANSION_BUDGET, cancelled) }
		if (cancelled()) return PathPlanResult.Cancelled

		PlanningDebugChannel.publishGraph(planner, preparation.initial.position)
		val route = timings.measure("route") {
			resolution.resolve(start, snapshotRevision, preparation.coarseExpansionBudget, world, cancelled)
		} ?: return noRoute(coarse)
		PlanningDebugChannel.publishRoute(route)
		LOG.info(
			"Planning startup {} -> {}: {} (route: {}), since-request={} ms; capture so far: {}",
			start, goal, timings, resolution.lastResolveReport,
			System.currentTimeMillis() - preparation.startedMillis, world.captureLedger(),
		)
		return walk(route).also { dumpOutcome(it, route) }
	}

	private fun noRoute(coarse: LongDStarLite.ComputeResult): PathPlanResult {
		if (cancelled()) return PathPlanResult.Cancelled
		val report = planner.routeFailureReport()
		LOG.warn("No coarse route {} -> {} after {} expansions: {}", start, goal, coarse.processedNodes, report)
		dump("no-route", "no coarse route; $report")
		return PathPlanResult.Failed("no coarse route to the goal")
	}

	private fun walk(route: CoarseRoutePlan): PathPlanResult {
		val probe = PlanningDebugChannel
		val legs = LegChain(
			world = world,
			states = legStates,
			starts = listOf(start) + preparation.waypoints,
			coarseExpansionBudget = preparation.coarseExpansionBudget,
			snapshotRevision = snapshotRevision,
			cancelled = cancelled,
			probe = probe,
			executor = LEG_RESOLVER,
			fieldExpansionBudget = FIELD_EXPANSION_BUDGET,
		)
		val field = legs.begin(route, resolution)
		return walkHorizon(
			route, planner, preparation.initial, preparation.profile, snapshot, preparation.seedConfig, execution,
			publish = publish,
			started = preparation.startedMillis,
			searchConfig = preparation.searchConfig,
			cancelled = cancelled,
			planningGeneration = planningGeneration,
			finalGoal = preparation.finalGoal,
			world = link(legs),
			field = field,
			probe = probe,
			onConcluded = {
				LOG.info("Trajectory search {} -> {}: {} {}", start, preparation.finalGoal, it, legs.ledger)
				PlanningDebugChannel.publishExhaustion(it, legs.ledger)
			},
			legs = legs,
			parallelism = preparation.plannerThreads,
		)
	}

	private fun link(legs: LegChain) = object : WorldLink {
		override fun await(timeoutMillis: Long) = world.awaitEvents(world.revision, timeoutMillis)

		override fun sync(route: CoarseRoutePlan) = legs.sync(route)

		override fun capturable(sectionX: Int, sectionZ: Int) = world.chunkCapturable(sectionX, sectionZ)
	}

	private fun dumpOutcome(outcome: PathPlanResult, route: CoarseRoutePlan) {
		if (outcome is PathPlanResult.Failed) {
			val note = buildString {
				append(outcome.reason)
				append("; route ").append(route.nodes.joinToString(" "))
				val anchors = planner.optimisticAnchors
				if (anchors.isNotEmpty()) {
					append("; anchors ").append(anchors.entries.joinToString(" ") { "${it.key}=%.1f".format(it.value) })
				}
			}
			dump("failed plan", note)
		}
		if (outcome is PathPlanResult.Planned && preparation.dumpAllPlans) {
			val path = outcome.path
			dump(
				"planned",
				"planned ${path.plan.frames.size} frames in ${System.currentTimeMillis() - preparation.startedMillis} ms; " +
						"${path.movementProfile()}; route ${route.nodes.joinToString(" ")}",
			)
		}
	}

	private fun dump(kind: String, note: String) {
		val directory = preparation.dumpDirectory ?: return
		runCatching {
			PlanDump.write(
				directory, snapshot, start, goal, preparation.initial, preparation.profile,
				preparation.moveOptions, preparation.seedConfig, note = note,
			)
		}.onFailure { LOG.error("Could not write the $kind dump", it) }
			.onSuccess { LOG.info("Wrote the {} dump: {}", kind, it) }
	}

	private fun awaitStartKnowledge() {
		awaitWhile(COLD_START_BODY_WAIT_MILLIS) { !snapshot.isKnown(start.x, start.y - 1, start.z) }
		if (world.chunkCapturable(goal.x shr 4, goal.z shr 4)) {
			awaitWhile(COLD_START_GOAL_WAIT_MILLIS) { !FrontierAnchors.goalResolved(snapshot, goal) }
		}
		awaitWhile(COLD_START_INTEREST_WAIT_MILLIS) { world.pendingDemand > 0 }
	}

	private inline fun awaitWhile(millis: Long, pending: () -> Boolean) {
		val deadline = System.nanoTime() + millis * 1_000_000L
		while (!cancelled() && System.nanoTime() < deadline && pending()) {
			if (!world.awaitEvents(world.revision, EVENT_POLL_MILLIS)) break
		}
	}

	private companion object {
		val PLANNER: ExecutorService = Executors.newSingleThreadExecutor { task ->
			Thread(task, "NeoLambda-PathPlanner").apply { isDaemon = true }
		}

		val LEG_RESOLVER: ExecutorService = Executors.newCachedThreadPool { task ->
			Thread(task, "NeoLambda-PathPlanner-legs").apply { isDaemon = true }
		}

		val FIELD_EXPANSION_BUDGET = 60.milliseconds

		const val COLD_START_BODY_WAIT_MILLIS = 1_500L
		const val COLD_START_GOAL_WAIT_MILLIS = 500L
		const val COLD_START_INTEREST_WAIT_MILLIS = 1_500L
		const val EVENT_POLL_MILLIS = 50L
	}
}
