package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.coarse.BreathingStops
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.FieldEdges
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.search.LegSource
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.streaming.PathingWorld
import org.apache.logging.log4j.LogManager

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

internal class CoarseFollower(
	private val coarse: CoarseService,
	private val world: PathingWorld?,
	private val view: CoarseVoxelView,
	private var leg: Int,
	first: CoarseService.Frame,
	startAir: Int = Vanilla.MAX_AIR,
	private val breathes: Boolean = true,
) : WorldLink, LegSource, AutoCloseable {
	private val events = world?.subscribe()
	override val startRevision: Long? = events?.drain()?.revision
	private val edges = FieldEdges(view, coarse.moves)
	private var frame = first
	private var abandoned = false
	private var adopted = 0
	private var syncNanos = 0L

	private class Breath(val goal: Stance, val route: CoarseRoutePlan, val field: CoarseValueField)

	private class Position(val leg: Int, val frame: CoarseService.Frame, val breath: Breath?, val targets: List<Stance>)

	private var breath: Breath? = null
	private var targets: List<Stance> = emptyList()
	private val history = ArrayDeque<Position>()
	private var checkedRoute: CoarseRoutePlan? = null
	private val hopeless = HashSet<Pair<Stance, Stance>>()

	val field = SwitchableValueField(edges.field(first.goal, first.labels, first.sequence))

	init {
		planBreaths(first.route, first.route.nodes.first(), startAir)
	}

	val route: CoarseRoutePlan get() = breath?.route ?: frame.route

	val anchorCount: Int get() = frame.anchorCount

	val ledger: String get() = "coarse frames=$adopted sync=${syncNanos / 1_000_000L}ms"

	override fun sync(route: CoarseRoutePlan, from: BodyState): WorldSyncResult {
		val started = System.nanoTime()
		coarse.follow(leg, from.stance, SpeedClass.of(from.speed))
		val batch = events?.drain()?.takeUnless { it.isEmpty }
		if (batch != null) {
			edges.invalidate(batch)
			field.invalidate()
		}
		val mutations = batch?.mutations.orEmpty()
		val chunks = batch?.chunks.orEmpty()
		val latest = coarse.frame(leg)
		val result = when {
			breath != null -> if (batch != null) WorldSyncResult.Woken(mutations, chunks, batch.revision) else WorldSyncResult.Quiet
			latest != null && latest !== frame -> {
				adopt(latest)
				planBreaths(latest.route, from.stance, from.state.air)
				WorldSyncResult.Changed(this.route, mutations, chunks, batch?.revision)
			}
			batch != null -> WorldSyncResult.Woken(mutations, chunks, batch.revision)
			else -> WorldSyncResult.Quiet
		}
		syncNanos += System.nanoTime() - started
		return result
	}

	override fun await(timeoutMillis: Long): Boolean {
		val world = world ?: return false
		val since = world.revision
		val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
		while (coarse.frame(leg) === frame) {
			val remaining = (deadline - System.nanoTime()) / 1_000_000L
			if (remaining <= 0L) return false
			if (world.awaitEvents(since, minOf(remaining, POLL_MILLIS))) return true
		}
		return true
	}

	override fun capturable(sectionX: Int, sectionZ: Int): Boolean = world?.chunkCapturable(sectionX, sectionZ) ?: true

	override fun hasNext(): Boolean {
		val active = breath
		if (active != null && (targets.isNotEmpty() || active.goal != coarse.goal(leg))) return true
		return !abandoned && leg + 1 < coarse.legCount
	}

	override fun handoff(touched: Stance): CoarseRoutePlan? {
		if (!hasNext()) return null
		val active = breath
		if (active != null && touched == active.goal && (targets.isNotEmpty() || active.goal != coarse.goal(leg))) {
			history.addLast(position())
			while (targets.isNotEmpty()) {
				val next = targets.first()
				targets = targets.drop(1)
				val built = breathFrom(touched, next) ?: continue
				enter(built)
				LOG.info("Breathing handoff at {}: next stop {}, route {} nodes", touched, next, built.route.nodes.size)
				return built.route
			}
			breath = null
			val rejoined = coarse.frame(leg) ?: frame
			adopt(rejoined)
			LOG.info("Breathing stops done at {}: back on the route toward {}", touched, rejoined.goal)
			return rejoined.route
		}
		val began = System.nanoTime()
		val next = coarse.awaitFrame(leg + 1, HANDOFF_WAIT_MILLIS)
		if (next == null) {
			LOG.warn("Route leg {} toward {} has no coarse route after {} ms; finishing at {} instead", leg + 1, coarse.goal(leg + 1), HANDOFF_WAIT_MILLIS, touched)
			abandoned = true
			return null
		}
		history.addLast(position())
		breath = null
		targets = emptyList()
		leg++
		adopt(next)
		LOG.info("Leg handoff at {}: next goal {}, route {} nodes, waited {} ms", touched, next.goal, next.route.nodes.size, (System.nanoTime() - began) / 1_000_000L)
		return next.route
	}

	override fun rewind(): CoarseRoutePlan {
		val previous = history.removeLastOrNull()
		if (previous == null) {
			leg--
			adopt(checkNotNull(coarse.frame(leg)) { "leg $leg was walked without a coarse frame" })
			LOG.info("Leg rewind: back toward {}", frame.goal)
			return frame.route
		}
		leg = previous.leg
		targets = previous.targets
		val restored = previous.breath
		if (restored != null) {
			frame = previous.frame
			enter(restored)
		} else {
			breath = null
			adopt(coarse.frame(leg) ?: previous.frame)
		}
		LOG.info("Leg rewind: back toward {}", route.goal)
		return route
	}

	override fun close() {
		events?.close()
	}

	private fun position() = Position(leg, frame, breath, targets)

	private fun planBreaths(route: CoarseRoutePlan, from: Stance, air: Int) {
		if (!breathes || route === checkedRoute) return
		checkedRoute = route
		val planned = BreathingStops.plan(view, coarse.moves, route.nodes, air, hopeless)
		if (planned.isEmpty()) return
		var remaining = planned
		while (remaining.isNotEmpty()) {
			val built = breathFrom(from, remaining.first())
			remaining = remaining.drop(1)
			if (built == null) continue
			targets = remaining
			enter(built)
			LOG.info("Breathing stops {} on the way to {}: first stop route {} nodes", planned, route.goal, built.route.nodes.size)
			return
		}
	}

	private fun breathFrom(from: Stance, to: Stance): Breath? {
		val planner = CoarsePlanner(view, coarse.moves, from, to)
		if (!planner.repair(maxExpansions = BREATH_EXPANSIONS).converged) return null
		planner.expandSearchField()
		val route = planner.routePlan(frame.sequence) ?: return null
		return Breath(to, route, planner.valueField())
	}

	private fun enter(next: Breath) {
		breath = next
		field.current = next.field
		PlanningDebugChannel.publishRoute(next.route)
	}

	private fun adopt(next: CoarseService.Frame) {
		frame = next
		field.current = edges.field(next.goal, next.labels, next.sequence)
		adopted++
		PlanningDebugChannel.publishRoute(next.route)
	}

	private companion object {
		const val HANDOFF_WAIT_MILLIS = 4_000L
		const val POLL_MILLIS = 25L
		const val BREATH_EXPANSIONS = 200_000
	}
}
