package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.pathing.core.PLANNER_THREAD_PRIORITY
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.search.MotionPlanResult
import com.lambda.pathing.search.PlanLeg
import com.lambda.pathing.search.PlanRefiner
import com.lambda.pathing.search.VirtualPacing
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PlanningJob(
	private val preparation: PlanningPreparation,
	private val journey: PlanningJourney,
	private val cancelled: () -> Boolean,
	private val execution: ExecutionFeedback,
	private val publish: (PublishedPath, Boolean) -> Unit,
	private val rehearsal: Rehearsal? = null,
	private val onRehearsal: (RehearsalPoint, PublishedPath) -> Unit = { _, _ -> },
	private val onRehearsalProgress: (String) -> Unit = {},
) {
	private val world = journey.world
	private val coarse = journey.coarse
	private val leg = journey.firstLegFor(preparation)
	private val snapshot = world.snapshot
	private val start = preparation.start
	private val goal = coarse.goal(leg)

	@Volatile
	var phase = "queued"
		private set

	@Volatile
	var phaseSince = System.currentTimeMillis()
		private set

	@Volatile
	var stats: SearchStats? = null
		private set

	@Volatile
	var statsAt = 0L
		private set

	@Volatile
	var publications = 0
		private set

	private val probe = object : SearchProbe by PlanningDebugChannel {
		override fun stats(view: SearchStats) {
			record(view)
			PlanningDebugChannel.stats(view)
		}
	}

	private fun record(view: SearchStats) {
		stats = view
		statsAt = System.currentTimeMillis()
	}

	private fun enter(next: String) {
		phase = next
		phaseSince = System.currentTimeMillis()
	}

	fun start(): CompletableFuture<PathPlanResult> = CompletableFuture.supplyAsync(::run, PLANNER)

	private fun run(): PathPlanResult = if (cancelled()) PathPlanResult.Cancelled else plan()

	private fun plan(): PathPlanResult {
		val began = System.nanoTime()
		enter("awaiting a coarse route from $start")
		val frame = coarse.awaitRoute(leg, start, cancelled)
		if (cancelled()) return PathPlanResult.Cancelled.also { enter("cancelled") }
		if (frame == null) return noRoute().also { enter("no coarse route") }
		enter("searching")
		PlanningDebugChannel.publishRoute(frame.route)
		LOG.info(
			"Planning startup {} -> {}: coarse route of {} nodes to {} after {} ms, since-request={} ms; capture so far: {}",
			start, goal, frame.route.nodes.size, frame.route.goal, (System.nanoTime() - began) / 1_000_000L,
			System.currentTimeMillis() - preparation.startedMillis, world.captureLedger(),
		)
		if (rehearsal != null) return rehearse(frame).also { enter("finished: ${it::class.simpleName}") }
		return CoarseFollower(coarse, world, snapshot, leg, frame, preparation.initial.air, preparation.profile.canDrown).use { follower ->
			walk(follower).let { if (it is PathPlanResult.Failed && mobsOnRoute(follower)) it.copy(mobs = true) else it }.also {
				enter("finished: ${it::class.simpleName}")
				dumpOutcome(it, follower)
			}
		}
	}

	private fun rehearse(first: CoarseService.Frame): PathPlanResult {
		val began = System.currentTimeMillis()
		var planLegs: List<PlanLeg>? = null
		var outcome: PathPlanResult = PathPlanResult.Cancelled
		var holdTicks = 0L
		for ((attempt, micros) in SPINE_MICROS_PER_EXPANSION.withIndex()) {
			if (cancelled()) return PathPlanResult.Cancelled
			val frame = if (attempt == 0) first else coarse.awaitRoute(leg, start, cancelled) ?: break
			enter("rehearsing the spine at $micros us per expansion")
			onRehearsalProgress(if (attempt == 0) "planning the spine" else "planning the spine, attempt ${attempt + 1}")
			val pacing = VirtualPacing(micros)
			outcome = CoarseFollower(coarse, world, snapshot, leg, frame, preparation.initial.air, preparation.profile.canDrown).use { follower ->
				walkHorizon(
					follower.route, journey.planner(leg), preparation.initial, preparation.profile, snapshot, preparation.seedConfig, pacing,
					publish = { path, _ -> pacing.offer(path) },
					started = preparation.startedMillis,
					searchConfig = preparation.searchConfig,
					world = follower,
					clock = pacing,
					cancelled = cancelled,
					finalGoal = preparation.finalGoal,
					field = follower.field,
					probe = probe,
					onConcluded = {
						record(it)
						LOG.info("Rehearsal spine at {} us {} -> {}: {} {}", micros, start, preparation.finalGoal, it, follower.ledger)
					},
					legs = follower,
					parallelism = preparation.plannerThreads,
				).also {
					dumpOutcome(it, follower)
					planLegs = follower.planLegs(leg)
				}
			}
			holdTicks = pacing.holdTicks
			if ((outcome as? PathPlanResult.Planned)?.path?.partial == false || outcome == PathPlanResult.Cancelled) break
		}
		val spine = (outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial } ?: return outcome
		LOG.info("Rehearsal spine: {} frames in {} ms, {} hold ticks", spine.plan.tape.frameCount, System.currentTimeMillis() - began, holdTicks)
		onRehearsal(RehearsalPoint(System.currentTimeMillis() - began, spine.plan.tape.frameCount, "spine"), spine)
		val legs = planLegs ?: return PathPlanResult.Planned(spine)
		val refiner = PlanRefiner(
			journey.planner(leg).moves.catalog, legs, preparation.initial, preparation.profile, snapshot, preparation.seedConfig,
			preparation.plannerThreads, cancelled,
		)
		var plan = PlanRefiner.Plan(spine.plan.segments, spine.legTouches)
		var best = spine
		val versions = HashMap<Int, Pair<PlanRefiner.Plan, PublishedPath>>()
		versions[spine.publicationSequence] = plan to spine
		val offered = HashSet<Int>()
		for ((levelIndex, level) in PlanRefiner.LEVELS.withIndex()) {
			var index = 0
			while (index < plan.segments.size && !cancelled()) {
				if (rehearsal?.performing == true) {
					val runningSequence = execution.adoptedSequence().toInt()
					val running = versions[runningSequence]
					if (running != null && best.publicationSequence > runningSequence) {
						if (execution.refusedSequence() >= best.publicationSequence) {
							plan = running.first
							best = running.second
						} else if (offered.add(best.publicationSequence)) {
							publish(best, true)
						}
					}
					val cursor = execution.cursorFrame() ?: 0
					if (plan.segments[index].startFrame < cursor + LIVE_REFINE_LEAD_FRAMES) {
						index++
						continue
					}
				}
				val progress = "level ${levelIndex + 1}/${PlanRefiner.LEVELS.size}, junction ${index + 1}/${plan.segments.size}"
				enter("refining at $progress")
				onRehearsalProgress(progress)
				val improved = refiner.refineAt(plan, index, level)
				val certified = improved?.let(refiner::certify)
				if (improved != null && certified != null) {
					plan = improved
					best = stamp(certified, best.publicationSequence + 1)
					LOG.info("Rehearsal refined at {}: {} frames after {} ms", progress, plan.frames, System.currentTimeMillis() - began)
					versions[best.publicationSequence] = plan to best
					onRehearsal(RehearsalPoint(System.currentTimeMillis() - began, best.plan.tape.frameCount, "L${levelIndex + 1} j${index + 1}"), best)
					if (rehearsal?.performing == true && offered.add(best.publicationSequence)) publish(best, true)
				}
				index++
			}
			if (cancelled()) break
		}
		LOG.info("Rehearsal finished: {} -> {} frames, {} expansions, {} grafts", spine.plan.tape.frameCount, best.plan.tape.frameCount, refiner.expansions, refiner.grafts)
		return PathPlanResult.Planned(best)
	}

	private fun stamp(certified: MotionPlanResult.Success, sequence: Int) = PublishedPath(
		route = certified.sourceRoute,
		plan = certified.plan,
		safeAnchorStance = certified.safeAnchorStance,
		safeAnchorFrame = certified.safeAnchorFrame,
		attempts = certified.attemptCount,
		planMillis = System.currentTimeMillis() - preparation.startedMillis,
		finalGoal = preparation.finalGoal,
		partial = false,
		publicationSequence = sequence,
		legTouches = certified.legTouches,
	)

	private fun mobsOnRoute(follower: CoarseFollower): Boolean =
		follower.route.nodes.asSequence().take(MOB_ROUTE_NODES).any { snapshot.entityAt(it.x, it.y, it.z) || snapshot.entityAt(it.x, it.y + 1, it.z) }

	private fun noRoute(): PathPlanResult {
		val reason = coarse.failure(leg, start) ?: "no coarse route to the goal"
		LOG.warn("No coarse route {} -> {}: {}", start, goal, reason)
		dump("no-route", reason)
		return PathPlanResult.Failed(reason)
	}

	private fun walk(follower: CoarseFollower): PathPlanResult = walkHorizon(
		follower.route, journey.planner(leg), preparation.initial, preparation.profile, snapshot, preparation.seedConfig, execution,
		publish = { path, running ->
			publications++
			publish(path, running)
		},
		started = preparation.startedMillis,
		searchConfig = preparation.searchConfig,
		cancelled = cancelled,
		finalGoal = preparation.finalGoal,
		world = follower,
		field = follower.field,
		probe = probe,
		onConcluded = {
			record(it)
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

	fun dumpEvent(kind: String, note: String, execution: PlanDump.ExecutionTrace?) {
		if (preparation.dumpDirectory == null || journey.terrain == null) return
		DUMPS.execute { dump(kind, "$kind: $note", execution) }
	}

	private fun dump(kind: String, note: String, execution: PlanDump.ExecutionTrace? = null) {
		val directory = preparation.dumpDirectory ?: return
		val terrain = journey.terrain ?: return
		runCatching {
			PlanDump.write(
				directory, terrain.store, terrain.recordedSections(), snapshot.bounds, start, goal, preparation.initial,
				preparation.profile, preparation.moveOptions, preparation.seedConfig, note = note, execution = execution,
				obstacles = journey.world.obstacleCells,
			)
		}.onFailure { LOG.error("Could not write the $kind dump", it) }
			.onSuccess { LOG.info("Wrote the {} dump: {}", kind, it) }
	}

	private companion object {
		const val MOB_ROUTE_NODES = 6

		val SPINE_MICROS_PER_EXPANSION = longArrayOf(83, 70, 100, 60, 120)

		const val LIVE_REFINE_LEAD_FRAMES = 60

		val PLANNER: ExecutorService = Executors.newSingleThreadExecutor { task ->
			Thread(task, "NeoLambda-PathPlanner").apply {
				isDaemon = true
				priority = PLANNER_THREAD_PRIORITY
			}
		}

		val DUMPS: ExecutorService = Executors.newSingleThreadExecutor { task ->
			Thread(task, "NeoLambda-PlanDumps").apply {
				isDaemon = true
				priority = PLANNER_THREAD_PRIORITY
			}
		}
	}
}
