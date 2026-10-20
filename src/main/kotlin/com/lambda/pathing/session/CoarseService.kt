package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FallEdges
import com.lambda.pathing.coarse.FieldLabels
import com.lambda.pathing.coarse.SharedEdges
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.PLANNER_THREAD_PRIORITY
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.util.math.Vec3d
import org.apache.logging.log4j.LogManager
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

internal class CoarseService(
	private val world: PathingWorld?,
	goals: List<Stance>,
	private val buildLegs: () -> List<CoarsePlanningState>,
	starts: List<Stance>,
	val expansionBudget: Int,
	private val expandsField: Boolean = true,
) : AutoCloseable {
	constructor(
		world: PathingWorld?,
		legs: List<CoarsePlanningState>,
		starts: List<Stance>,
		expansionBudget: Int,
		expandsField: Boolean = true,
	) : this(world, legs.map { it.goal }, { legs }, starts, expansionBudget, expandsField)

	class Frame(
		val sequence: Long,
		val start: Stance,
		val goal: Stance,
		val route: CoarseRoutePlan,
		val labels: FieldLabels,
		val anchorCount: Int,
		val publishedNanos: Long,
	)

	private class Request(val leg: Int, val stance: Stance, val speed: SpeedClass)

	private class Failure(val start: Stance, val reason: String)

	private val goals = goals.toTypedArray()

	private val retargets = HashMap<Int, Stance>()

	private val lock = ReentrantLock()
	private val published = lock.newCondition()
	private val frames = arrayOfNulls<Frame>(goals.size)
	private val failures = arrayOfNulls<Failure>(goals.size)
	private var request: Request? = null

	private val events = world?.subscribe()
	private val starts = starts.toTypedArray()
	private val speeds = Array(goals.size) { SpeedClass.STOPPED }
	private val dirty = BooleanArray(goals.size) { true }

	@Volatile
	private var built: List<CoarsePlanningState>? = null

	private val legs: List<CoarsePlanningState> get() = built ?: build()

	private fun build(): List<CoarsePlanningState> = synchronized(this) { built ?: buildLegs().also { built = it } }
	private var revision = 0L
	private val settledRevisions = AtomicLongArray(goals.size)

	private val backlogs = BooleanArray(goals.size)
	private var sequence = 0L
	private var settled = -1

	@Volatile
	private var closed = false

	@Volatile
	private var settling: PhaseTimings? = null

	@Volatile
	private var settlingLeg = -1

	val legCount: Int get() = goals.size

	val moves: SimpleMoveLibrary get() = legs.first().planner.moves

	fun goal(leg: Int): Stance = lock.withLock { goals[leg] }

	fun retarget(leg: Int, goal: Stance) = lock.withLock {
		if (goals[leg] == goal) return@withLock
		goals[leg] = goal
		retargets[leg] = goal
		failures[leg] = null
		published.signalAll()
	}

	fun planner(leg: Int): CoarsePlanner = legs[leg].planner

	fun start() {
		Thread(::run, "NeoLambda-PathPlanner-coarse").apply {
			isDaemon = true
			priority = PLANNER_THREAD_PRIORITY
		}.start()
	}

	override fun close() {
		closed = true
		events?.close()
		lock.withLock { published.signalAll() }
	}

	fun follow(leg: Int, stance: Stance, speed: SpeedClass) = lock.withLock {
		val current = request
		if (current == null || current.leg != leg || current.stance != stance || current.speed != speed) {
			request = Request(leg, stance, speed)
		}
	}

	fun frame(leg: Int): Frame? = lock.withLock { frames[leg] }

	fun activity(): String = buildString {
		val leg = lock.withLock { request }?.leg ?: 0
		val active = settling
		if (active == null) append("idle") else {
			append("settling leg ").append(settlingLeg).append(" for ").append(active.elapsedMillis).append(" ms")
			active.current?.let { append(" (").append(it).append(')') }
		}
		val (framed, failed) = lock.withLock { frames[leg] to failures[leg] }
		if (framed == null) append(", no frame for leg ").append(leg) else {
			append(", leg ").append(leg).append(" frame #").append(framed.sequence)
			append(' ').append((System.nanoTime() - framed.publishedNanos) / 1_000_000L).append(" ms old from ").append(framed.start)
			append(", route ").append(framed.route.nodes.size).append(" nodes to ").append(framed.route.goal)
		}
		failed?.let { append(", failure from ").append(it.start).append(": ").append(it.reason) }
	}

	fun awaitFrame(leg: Int, timeoutMillis: Long, accept: (Frame) -> Boolean = { true }): Frame? = lock.withLock {
		var remaining = timeoutMillis * 1_000_000L
		var frame = standing(leg)?.takeIf(accept)
		while (frame == null && !closed && remaining > 0L) {
			remaining = published.awaitNanos(remaining)
			frame = standing(leg)?.takeIf(accept)
		}
		frame
	}

	private fun standing(leg: Int): Frame? = frames[leg]?.takeUnless { failures[leg]?.start == it.start }

	fun awaitLeg(leg: Int): Frame? = lock.withLock {
		var frame = standing(leg)
		while (frame == null && !closed && failures[leg]?.start != starts[leg]) {
			published.awaitNanos(ROUTE_POLL_MILLIS * 1_000_000L)
			frame = standing(leg)
		}
		frame
	}

	fun awaitRoute(leg: Int, start: Stance, cancelled: () -> Boolean, arrives: (Stance) -> Boolean = { it == goals[leg] }): Frame? {
		val goal = goals[leg]
		val waitUntil = System.nanoTime() + FULL_ROUTE_WAIT_MILLIS * 1_000_000L
		var frame: Frame? = null
		var hopelessSince = 0L
		while (!cancelled() && !closed) {
			follow(leg, start, SpeedClass.STOPPED)
			val next = awaitFrame(leg, ROUTE_POLL_MILLIS) { it.start == start && it !== frame }
			if (next != null) {
				frame = next
				if (arrives(next.route.goal) || !awaitsGoalChunk(goal)) return next
			} else if (frame == null && hopeless(leg, start)) {
				if (hopelessSince == 0L) hopelessSince = System.nanoTime()
				else if (System.nanoTime() - hopelessSince > HOPELESS_MILLIS * 1_000_000L) return null
			} else hopelessSince = 0L
			if (frame != null && (!learning(leg) || System.nanoTime() > waitUntil)) return frame
		}
		return frame
	}

	private fun awaitsGoalChunk(goal: Stance): Boolean {
		val world = world ?: return false
		return !world.readable(goal.x shr 4, goal.z shr 4) && world.loadable(goal.x shr 4, goal.z shr 4)
	}

	private fun learning(leg: Int): Boolean {
		val world = world ?: return false
		return settling != null || settledRevisions.get(leg) != world.revision
	}

	fun failure(leg: Int, start: Stance): String? = lock.withLock { failures[leg]?.takeIf { it.start == start }?.reason }

	fun settle() {
		while (!closed) if (!advance()) return
	}

	private fun hopeless(leg: Int, start: Stance): Boolean = lock.withLock { failures[leg]?.start == start }

	private fun run() {
		try {
			build()
			while (!closed) {
				val began = System.nanoTime()
				val worked = advance()
				if (!worked) awaitWork()
				val rest = EPOCH_NANOS - (System.nanoTime() - began)
				if (rest > 0L && !closed) Thread.sleep(rest / 1_000_000L)
			}
		} catch (failure: Throwable) {
			LOG.error("The coarse planner thread failed", failure)
			closed = true
			lock.withLock {
				for (leg in goals.indices) failures[leg] = Failure(starts[leg], "the coarse planner crashed: ${failure.message}")
				published.signalAll()
			}
		}
	}

	private fun awaitWork() {
		val world = world ?: return
		val seen = revision
		val wanted = lock.withLock { request }
		val until = System.nanoTime() + IDLE_MILLIS * 1_000_000L
		while (!closed && System.nanoTime() < until) {
			if (world.awaitEvents(seen, POLL_MILLIS)) return
			if (lock.withLock { request !== wanted || retargets.isNotEmpty() }) return
		}
	}

	private fun advance(): Boolean {
		val batch = events?.drain()
		val moved = lock.withLock { HashMap(retargets).also { retargets.clear() } }
		for ((leg, goal) in moved) {
			legs[leg].retarget(goal, starts[leg], speeds[leg])
			dirty[leg] = true
		}
		if (batch != null && !batch.isEmpty) {
			revision = batch.revision
			val applied = HashSet<SharedEdges>()
			for (leg in legs.indices) {
				legs[leg].applyEvents(batch, maxStances = 0, applied = applied)
				dirty[leg] = true
			}
		}
		val wanted = lock.withLock { request }
		val active = wanted?.leg ?: 0
		if (wanted != null) moveStart(active, wanted.stance, wanted.speed)
		val waiting = (active + 1 until legs.size).firstOrNull { dirty[it] }
		val activeDue = dirty[active] || reframe(active)
		val leg = when {
			waiting != null && (!activeDue || settled == active) -> waiting
			activeDue -> active
			else -> return false
		}
		settled = leg
		settle(leg)
		return true
	}

	private fun moveStart(leg: Int, stance: Stance, speed: SpeedClass) {
		if (starts[leg] == stance && speeds[leg] == speed) return
		starts[leg] = stance
		speeds[leg] = speed
		legs[leg].planner.updateStart(stance, speed)
	}

	private fun reframe(leg: Int): Boolean {
		val framed = lock.withLock { frames[leg] } ?: return failure(leg, starts[leg]) == null
		val start = starts[leg]
		if (framed.start == start) return false
		val moved = maxOf(abs(start.x - framed.start.x), abs(start.y - framed.start.y), abs(start.z - framed.start.z))
		return moved > REFRAME_BLOCKS || System.nanoTime() - framed.publishedNanos > REFRAME_NANOS
	}

	private fun settle(leg: Int) {
		dirty[leg] = false
		val through = revision
		val timings = PhaseTimings()
		val planner = legs[leg].planner
		val synced = planner.syncedStances
		val exact = planner.exactEdgeChecks
		val arrivals = planner.arrivalEdgeChecks
		settlingLeg = leg
		settling = timings
		try {
			settleRoute(leg, timings)
		} finally {
			if (!backlogs[leg]) settledRevisions.set(leg, through)
			settling = null
		}
		val millis = timings.elapsedMillis
		if (millis >= SLOW_SETTLE_MILLIS) {
			LOG.info(
				"Coarse leg {} from {} settled ({} graph nodes, {} anchors, {} stances synced, {} exact edge checks, {} predecessor edge checks): {}",
				leg, starts[leg], planner.graphSize, planner.anchorCount, planner.syncedStances - synced,
				planner.exactEdgeChecks - exact, planner.arrivalEdgeChecks - arrivals, timings,
			)
		}
	}

	private fun settleRoute(leg: Int, timings: PhaseTimings) {
		val state = legs[leg]
		val planner = state.planner
		val start = starts[leg]
		backlogs[leg] = timings.measure("sync") { planner.continueSync(SYNC_SLICE_STANCES, near = start) }
		if (backlogs[leg]) dirty[leg] = true
		val repair = timings.measure("repair") { planner.repair(maxExpansions = expansionBudget, cancelled = ::closed) }
		if (closed) return
		if (!repair.converged && !repair.provenUnreachable) {
			val stall = repair.stall?.let { "; stalled: $it" } ?: ""
			return fail(leg, start, "coarse search did not converge within $expansionBudget expansions$stall")
		}
		val route = if (repair.provenUnreachable) null else {
			val framed = lock.withLock { frames[leg] } != null
			if (expandsField && framed && !backlogs[leg]) timings.measure("field") { planner.expandSearchField(cancelled = ::closed) }
			if (!framed) dirty[leg] = true
			timings.measure("route") { state.routePlan(revision, ::closed, expansionBudget) }
		}
		if (closed) return
		if (route == null) {
			val group = state.edges
			if (state.forgetMemory(start, speeds[leg])) {
				LOG.info("Remembered terrain leaves no coarse route from {} to {}; planning leg {} without it", start, state.goal, leg)
				for (other in legs.indices) if (other != leg && group != null && legs[other].edges === group) legs[other].rebuild(starts[other], speeds[other])
				for (other in legs.indices) if (other == leg || group != null && legs[other].edges === state.edges) dirty[other] = true
				return
			}
			val why = planner.unreachableReport ?: planner.routeFailureReport()
			return fail(leg, start, "no coarse route to the goal: $why")
		}
		publish(leg, start, route, timings)
		if (timings.measure("recall") { state.recallMemory(route, start) }) {
			val group = state.edges
			for (other in legs.indices) if (other == leg || group != null && legs[other].edges === group) dirty[other] = true
		}
	}

	private fun publish(leg: Int, start: Stance, route: CoarseRoutePlan, timings: PhaseTimings) {
		val planner = legs[leg].planner
		val frame = Frame(
			sequence = ++sequence,
			start = start,
			goal = legs[leg].goal,
			route = route,
			labels = timings.measure("labels") {
				val body = lock.withLock { request }?.takeIf { it.leg == leg }?.stance ?: start
				planner.labels(listOf(start, body), route.nodes, LABEL_RADIUS_BLOCKS, ROUTE_LABEL_BAND_BLOCKS, LABEL_HEIGHT_BLOCKS)
			},
			anchorCount = planner.anchorCount,
			publishedNanos = System.nanoTime(),
		)
		lock.withLock {
			frames[leg] = frame
			failures[leg] = null
			published.signalAll()
		}
		PlanningDebugChannel.refreshGraph(planner, Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5))
		PlanningDebugChannel.publishCoarse(PlanningDebugChannel.CoarseSample(frame.sequence, planner.graphSize, frame.anchorCount, timings.elapsedMillis))
	}

	private fun fail(leg: Int, start: Stance, reason: String) = lock.withLock {
		failures[leg] = Failure(start, reason)
		published.signalAll()
	}

	internal companion object {
		const val FULL_ROUTE_WAIT_MILLIS = 8_000L
		const val HOPELESS_MILLIS = 1_000L
		const val ROUTE_POLL_MILLIS = 100L
		const val EPOCH_NANOS = 100_000_000L
		const val IDLE_MILLIS = 250L
		const val POLL_MILLIS = 25L
		const val REFRAME_BLOCKS = 12
		const val REFRAME_NANOS = 300_000_000L
		const val SLOW_SETTLE_MILLIS = 1_000L
		const val SYNC_SLICE_STANCES = 3_000
		const val LABEL_RADIUS_BLOCKS = 128
		const val ROUTE_LABEL_BAND_BLOCKS = 48
		const val LABEL_HEIGHT_BLOCKS = FallEdges.MAX_DEPTH + 32
	}
}
