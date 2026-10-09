package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FieldLabels
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.world.streaming.InterestTier
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.util.math.Vec3d
import org.apache.logging.log4j.LogManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

internal class CoarseService(
	private val world: PathingWorld?,
	private val legs: List<CoarsePlanningState>,
	starts: List<Stance>,
	private val expansionBudget: Int,
) : AutoCloseable {
	class Frame(
		val sequence: Long,
		val start: Stance,
		val goal: Stance,
		val route: CoarseRoutePlan,
		val labels: FieldLabels,
		val anchors: Map<Stance, Double>,
		val publishedNanos: Long,
	)

	private class Request(val leg: Int, val stance: Stance, val speed: SpeedClass)

	private class Failure(val start: Stance, val reason: String, val final: Boolean)

	private val lock = ReentrantLock()
	private val published = lock.newCondition()
	private val frames = arrayOfNulls<Frame>(legs.size)
	private val failures = arrayOfNulls<Failure>(legs.size)
	private var request: Request? = null

	private val events = world?.subscribe()
	private val resolutions = legs.map(::RouteResolution)
	private val starts = starts.toTypedArray()
	private val speeds = Array(legs.size) { SpeedClass.STOPPED }
	private val dirty = BooleanArray(legs.size) { true }
	private var revision = 0L
	private var sequence = 0L
	private var settled = -1

	@Volatile
	private var closed = false

	val legCount: Int get() = legs.size

	val moves: SimpleMoveLibrary get() = legs.first().planner.moves

	fun goal(leg: Int): Stance = legs[leg].goal

	fun start() {
		Thread(::run, "NeoLambda-PathPlanner-coarse").apply { isDaemon = true }.start()
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

	fun awaitFrame(leg: Int, timeoutMillis: Long, accept: (Frame) -> Boolean = { true }): Frame? = lock.withLock {
		var remaining = timeoutMillis * 1_000_000L
		var frame = frames[leg]?.takeIf(accept)
		while (frame == null && !closed && remaining > 0L) {
			remaining = published.awaitNanos(remaining)
			frame = frames[leg]?.takeIf(accept)
		}
		frame
	}

	fun awaitRoute(leg: Int, start: Stance, cancelled: () -> Boolean): Frame? {
		val goal = legs[leg].goal
		val deadline = System.nanoTime() + ROUTE_PATIENCE_MILLIS * 1_000_000L
		var frame: Frame? = null
		var settleBy = Long.MAX_VALUE
		var hopelessSince = 0L
		while (!cancelled() && System.nanoTime() < minOf(deadline, settleBy)) {
			val next = awaitFrame(leg, ROUTE_POLL_MILLIS) { it.start == start && it !== frame }
			if (next != null) {
				frame = next
				if (next.route.goal == goal || world?.chunkCapturable(goal.x shr 4, goal.z shr 4) != true) return next
				if (settleBy == Long.MAX_VALUE) settleBy = System.nanoTime() + TERMINAL_WAIT_MILLIS * 1_000_000L
			} else if (frame == null && hopeless(leg, start)) {
				if (hopelessSince == 0L) hopelessSince = System.nanoTime()
				else if (System.nanoTime() - hopelessSince > HOPELESS_MILLIS * 1_000_000L) return null
			} else hopelessSince = 0L
		}
		return frame
	}

	fun failure(leg: Int, start: Stance): String? = lock.withLock { failures[leg]?.takeIf { it.start == start }?.reason }

	fun settle() {
		while (!closed && advance()) Unit
	}

	private fun hopeless(leg: Int, start: Stance): Boolean = lock.withLock { failures[leg]?.let { it.start == start && it.final } == true }

	private fun run() {
		try {
			while (!closed) {
				val began = System.nanoTime()
				val worked = advance()
				if (!worked) awaitWork()
				val rest = EPOCH_NANOS - (System.nanoTime() - began)
				if (rest > 0L && !closed) Thread.sleep(rest / 1_000_000L)
			}
		} catch (failure: Throwable) {
			LOG.error("The coarse planner thread failed", failure)
			lock.withLock {
				for (leg in legs.indices) failures[leg] = Failure(starts[leg], "the coarse planner crashed: ${failure.message}", final = true)
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
			if (lock.withLock { request } !== wanted) return
		}
	}

	private fun advance(): Boolean {
		val batch = events?.drain()
		if (batch != null && !batch.isEmpty) {
			revision = batch.revision
			for (leg in legs.indices) {
				legs[leg].applyEvents(batch, maxStances = 0)
				dirty[leg] = true
			}
		}
		val wanted = lock.withLock { request }
		val active = wanted?.leg ?: 0
		if (wanted != null) moveStart(active, wanted.stance, wanted.speed)
		val waiting = legs.indices.firstOrNull { it != active && dirty[it] }
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
		val timings = PhaseTimings()
		settleRoute(leg, timings)
		val millis = timings.elapsedMillis
		if (millis >= SLOW_SETTLE_MILLIS) {
			LOG.info("Coarse leg {} from {} settled ({} graph nodes): {} (route: {})", leg, starts[leg], legs[leg].planner.graphSize, timings, resolutions[leg].lastResolveReport)
		}
	}

	private fun settleRoute(leg: Int, timings: PhaseTimings) {
		val state = legs[leg]
		val planner = state.planner
		val start = starts[leg]
		timings.measure("sync") { planner.continueSync() }
		val repair = timings.measure("repair") { planner.repair(maxExpansions = expansionBudget, cancelled = ::closed) }
		if (closed) return
		if (!repair.converged) {
			val stall = repair.stall?.let { "; stalled: $it" } ?: ""
			return fail(leg, start, "coarse search did not converge within $expansionBudget expansions$stall", final = true)
		}
		timings.measure("field") { planner.expandSearchField(cancelled = ::closed) }
		val resolution = resolutions[leg]
		val route = timings.measure("route") { resolution.resolve(start, revision, expansionBudget, cancelled = ::closed) }
		if (closed) return
		val routed = lock.withLock { frames[leg] } != null
		world?.let { requestKnowledge(it, state, route, if (routed) InterestTier.CORRIDOR else InterestTier.DEMAND) }
		if (route == null) {
			val capturing = world != null && world.pendingInterest > 0
			return fail(leg, start, "no coarse route to the goal: ${planner.routeFailureReport()}", final = !capturing)
		}
		publish(leg, start, route, timings)
		val extended = timings.measure("extend") { resolution.extend(route, start, revision, expansionBudget, cancelled = ::closed) }
		if (extended != null && !closed) publish(leg, start, extended, timings)
	}

	private fun publish(leg: Int, start: Stance, route: CoarseRoutePlan, timings: PhaseTimings) {
		val planner = legs[leg].planner
		val frame = Frame(
			sequence = ++sequence,
			start = start,
			goal = legs[leg].goal,
			route = route,
			labels = timings.measure("labels") { planner.labels(start, LABEL_RADIUS_BLOCKS, LABEL_HEIGHT_BLOCKS) },
			anchors = planner.optimisticAnchors,
			publishedNanos = System.nanoTime(),
		)
		lock.withLock {
			frames[leg] = frame
			failures[leg] = null
			published.signalAll()
		}
		PlanningDebugChannel.refreshGraph(planner, Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5))
		PlanningDebugChannel.publishCoarse(PlanningDebugChannel.CoarseSample(frame.sequence, planner.graphSize, frame.anchors.size, timings.elapsedMillis))
	}

	private fun requestKnowledge(world: PathingWorld, state: CoarsePlanningState, route: CoarseRoutePlan?, tier: InterestTier) {
		state.demandCaptureLag(world, tier)
		if (route != null && route.goal != state.goal) world.interestAround(route.goal, tier)
	}

	private fun fail(leg: Int, start: Stance, reason: String, final: Boolean) = lock.withLock {
		failures[leg] = Failure(start, reason, final)
		published.signalAll()
	}

	private companion object {
		const val ROUTE_PATIENCE_MILLIS = 20_000L
		const val TERMINAL_WAIT_MILLIS = 1_500L
		const val HOPELESS_MILLIS = 1_000L
		const val ROUTE_POLL_MILLIS = 100L
		const val EPOCH_NANOS = 100_000_000L
		const val IDLE_MILLIS = 250L
		const val POLL_MILLIS = 25L
		const val REFRAME_BLOCKS = 12
		const val REFRAME_NANOS = 300_000_000L
		const val SLOW_SETTLE_MILLIS = 1_000L
		const val LABEL_RADIUS_BLOCKS = 128
		const val LABEL_HEIGHT_BLOCKS = 64
	}
}
