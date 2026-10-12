package com.lambda.pathing.session

import com.lambda.Lambda
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FieldEdges
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
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
	view: CoarseVoxelView,
	private var leg: Int,
	first: CoarseService.Frame,
) : WorldLink, LegSource, AutoCloseable {
	private val events = world?.subscribe()
	override val startRevision: Long? = events?.drain()?.revision
	private val edges = FieldEdges(view, coarse.moves)
	private var frame = first
	private var abandoned = false
	private var adopted = 0
	private var syncNanos = 0L

	val field = SwitchableValueField(edges.field(first.goal, first.labels, first.sequence))

	val route: CoarseRoutePlan get() = frame.route

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
			latest != null && latest !== frame -> {
				adopt(latest)
				WorldSyncResult.Changed(latest.route, mutations, chunks, batch?.revision)
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

	override fun hasNext(): Boolean = !abandoned && leg + 1 < coarse.legCount

	override fun handoff(touched: Stance): CoarseRoutePlan? {
		if (!hasNext()) return null
		val began = System.nanoTime()
		val next = coarse.awaitFrame(leg + 1, HANDOFF_WAIT_MILLIS)
		if (next == null) {
			LOG.warn("Route leg {} toward {} has no coarse route after {} ms; finishing at {} instead", leg + 1, coarse.goal(leg + 1), HANDOFF_WAIT_MILLIS, touched)
			abandoned = true
			return null
		}
		leg++
		adopt(next)
		LOG.info("Leg handoff at {}: next goal {}, route {} nodes, waited {} ms", touched, next.goal, next.route.nodes.size, (System.nanoTime() - began) / 1_000_000L)
		return next.route
	}

	override fun rewind(): CoarseRoutePlan {
		leg--
		adopt(checkNotNull(coarse.frame(leg)) { "leg $leg was walked without a coarse frame" })
		LOG.info("Leg rewind: back toward {}", frame.goal)
		return frame.route
	}

	override fun close() {
		events?.close()
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
	}
}
