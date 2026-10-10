package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.PublishedPath
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PlanningJob(
	private val preparation: PlanningPreparation,
	private val journey: PlanningJourney,
	private val cancelled: () -> Boolean,
	private val execution: ExecutionFeedback,
	private val publish: (PublishedPath, Boolean) -> Unit,
) {
	private val world = journey.world
	private val coarse = journey.coarse
	private val leg = journey.firstLegFor(preparation)
	private val snapshot = world.snapshot
	private val start = preparation.start
	private val goal = coarse.goal(leg)

	fun start(): CompletableFuture<PathPlanResult> = CompletableFuture.supplyAsync(::run, PLANNER)

	private fun run(): PathPlanResult = if (cancelled()) PathPlanResult.Cancelled else plan()

	private fun plan(): PathPlanResult {
		val began = System.nanoTime()
		coarse.follow(leg, start, SpeedClass.STOPPED)
		val frame = coarse.awaitRoute(leg, start, cancelled)
		if (cancelled()) return PathPlanResult.Cancelled
		if (frame == null) return noRoute()
		PlanningDebugChannel.publishRoute(frame.route)
		LOG.info(
			"Planning startup {} -> {}: coarse route of {} nodes to {} after {} ms, since-request={} ms; capture so far: {}",
			start, goal, frame.route.nodes.size, frame.route.goal, (System.nanoTime() - began) / 1_000_000L,
			System.currentTimeMillis() - preparation.startedMillis, world.captureLedger(),
		)
		return CoarseFollower(coarse, world, snapshot, leg, frame).use { follower ->
			walk(frame, follower).also { dumpOutcome(it, follower) }
		}
	}

	private fun noRoute(): PathPlanResult {
		val reason = coarse.failure(leg, start) ?: "no coarse route to the goal"
		LOG.warn("No coarse route {} -> {}: {}", start, goal, reason)
		dump("no-route", reason)
		return PathPlanResult.Failed(reason)
	}

	private fun walk(frame: CoarseService.Frame, follower: CoarseFollower): PathPlanResult = walkHorizon(
		frame.route, journey.planner(leg), preparation.initial, preparation.profile, snapshot, preparation.seedConfig, execution,
		publish = publish,
		started = preparation.startedMillis,
		searchConfig = preparation.searchConfig,
		cancelled = cancelled,
		finalGoal = preparation.finalGoal,
		world = follower,
		field = follower.field,
		probe = PlanningDebugChannel,
		onConcluded = {
			LOG.info("Trajectory search {} -> {}: {} {}", start, preparation.finalGoal, it, follower.ledger)
			PlanningDebugChannel.publishExhaustion(it, follower.ledger)
		},
		legs = follower,
		parallelism = preparation.plannerThreads,
	)

	private fun dumpOutcome(outcome: PathPlanResult, follower: CoarseFollower) {
		if (outcome is PathPlanResult.Failed) {
			val note = buildString {
				append(outcome.reason)
				append("; route ").append(follower.route.nodes.joinToString(" "))
				append("; anchors ").append(follower.anchorCount)
			}
			dump("failed plan", note)
		}
		if (outcome is PathPlanResult.Planned && preparation.dumpAllPlans) {
			val path = outcome.path
			dump(
				"planned",
				"planned ${path.plan.frames.size} frames in ${System.currentTimeMillis() - preparation.startedMillis} ms; " +
						"${path.movementProfile()}; route ${path.route.nodes.joinToString(" ")}",
			)
		}
	}

	private fun dump(kind: String, note: String) {
		val directory = preparation.dumpDirectory ?: return
		val terrain = journey.terrain ?: return
		runCatching {
			PlanDump.write(
				directory, terrain.store, terrain.recordedSections(), snapshot.bounds, start, goal, preparation.initial,
				preparation.profile, preparation.moveOptions, preparation.seedConfig, note = note,
			)
		}.onFailure { LOG.error("Could not write the $kind dump", it) }
			.onSuccess { LOG.info("Wrote the {} dump: {}", kind, it) }
	}

	private companion object {
		val PLANNER: ExecutorService = Executors.newSingleThreadExecutor { task ->
			Thread(task, "NeoLambda-PathPlanner").apply { isDaemon = true }
		}
	}
}
