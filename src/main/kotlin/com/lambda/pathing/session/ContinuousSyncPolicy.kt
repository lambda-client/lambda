package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.WorldEventBatch
import net.minecraft.util.math.Vec3d

internal class ContinuousSyncPolicy(
	private val world: PathingWorld,
	private val coarseState: CoarsePlanningState,
	private val resolution: RouteResolution,
	private val field: ValueField,
	private val start: Stance,
	private val finalGoal: Stance,
	private val snapshotRevision: Long,
	private val cancelled: () -> Boolean,
	private val probe: SearchProbe,

	private val onBatch: (WorldEventBatch) -> Unit = {},
) : (CoarseRoutePlan) -> WorldSyncResult {
	private var lastQuietExtension = 0L

	private var syncNanos = 0L
	private var resolveNanos = 0L
	private var resolves = 0

	val ledger: String get() = "coarseSync=${syncNanos / 1_000_000L}ms[apply ${(syncNanos - resolveNanos) / 1_000_000L}, resolve ${resolveNanos / 1_000_000L} x$resolves]"

	override fun invoke(current: CoarseRoutePlan): WorldSyncResult {
		val batch = world.drainEvents()
		if (!batch.isEmpty) onBatch(batch)
		if (batch.isEmpty) {

			val t = System.nanoTime()
			val more = coarseState.continueSync(SYNC_SLICE_STANCES)
			syncNanos += System.nanoTime() - t
			if (more) return WorldSyncResult.Woken()

			val extending = current.goal != finalGoal
			if (!extending) return WorldSyncResult.Quiet
			val now = System.currentTimeMillis()
			if (now - lastQuietExtension < QUIET_EXTENSION_INTERVAL_MILLIS) {
				return WorldSyncResult.Quiet
			}
			lastQuietExtension = now
			val resolveStarted = System.nanoTime()
			val next = resolution.resolve(
				start, snapshotRevision, RESOLVE_SLICE_EXPANSIONS,
			) { cancelled() }
			resolves++
			resolveNanos += System.nanoTime() - resolveStarted
			syncNanos += System.nanoTime() - resolveStarted
			refreshGraph(next ?: current)
			if (next == null || next.nodes == current.nodes) return WorldSyncResult.Quiet
			return WorldSyncResult.Changed(next)
		}

		val t = System.nanoTime()
		coarseState.applyEvents(batch, SYNC_SLICE_STANCES)
		syncNanos += System.nanoTime() - t
		val extending = current.goal != finalGoal
		val mutated = batch.mutations.isNotEmpty() || batch.chunks.isNotEmpty()

		val mutatedSections = mutatedSectionSet(batch)
		if (!routeNeighborhoodTouched(current, batch) || (!extending && !mutated)) {
			return WorldSyncResult.Woken(mutatedSections)
		}
		field.invalidate(batch.sections)

		val routeSections = current.dependencies.mapTo(HashSet()) {
			PathingSection.containing(it)
		}
		val routeAffected = extending ||
				batch.mutations.any { it in routeSections } ||
				batch.sections.any { it in routeSections }
		probe.sync(
			batch.sections.size, batch.mutations.size, batch.chunks.size,
			routeAffected, extending,
		)
		val resolveStarted = System.nanoTime()
		val next = if (routeAffected) {
			resolves++
			resolution.resolve(
				start, snapshotRevision, RESOLVE_SLICE_EXPANSIONS,
			) { cancelled() }
		} else null
		resolveNanos += System.nanoTime() - resolveStarted
		syncNanos += System.nanoTime() - resolveStarted
		if (next != null) refreshGraph(next)
		next?.goal?.let { terminal ->
			world.interestBlocks(
				terminal.x - 32, terminal.y - 16, terminal.z - 32,
				terminal.x + 32, terminal.y + 16, terminal.z + 32,
				InterestTier.CORRIDOR,
			)
		}
		return WorldSyncResult.Changed(next, mutatedSections)
	}

	private fun refreshGraph(route: CoarseRoutePlan) {
		val at = route.nodes.first()
		PlanningDebugChannel.refreshGraph(
			coarseState.planner,
			Vec3d(at.x + 0.5, at.y.toDouble(), at.z + 0.5),
		)
	}

	private fun mutatedSectionSet(batch: WorldEventBatch): Set<PathingSection> {
		if (batch.mutations.isEmpty() && batch.chunks.isEmpty()) return emptySet()
		val out = HashSet<PathingSection>(batch.mutations)
		if (batch.chunks.isNotEmpty()) {
			val chunks = batch.chunks
			world.snapshot.forEachSectionKey { key ->
				val x = net.minecraft.util.math.ChunkSectionPos.unpackX(key)
				val z = net.minecraft.util.math.ChunkSectionPos.unpackZ(key)
				if (PathingChunk(x, z) in chunks) {
					out += PathingSection(x, net.minecraft.util.math.ChunkSectionPos.unpackY(key), z)
				}
			}
		}
		return out
	}

	private fun routeNeighborhoodTouched(
		route: CoarseRoutePlan,
		batch: WorldEventBatch,
	): Boolean {
		if (batch.sections.isEmpty() && batch.chunks.isEmpty()) return false
		var minX = Int.MAX_VALUE
		var maxX = Int.MIN_VALUE
		var minY = Int.MAX_VALUE
		var maxY = Int.MIN_VALUE
		var minZ = Int.MAX_VALUE
		var maxZ = Int.MIN_VALUE
		route.nodes.forEach { node ->
			minX = minOf(minX, node.x shr 4); maxX = maxOf(maxX, node.x shr 4)
			minY = minOf(minY, node.y shr 4); maxY = maxOf(maxY, node.y shr 4)
			minZ = minOf(minZ, node.z shr 4); maxZ = maxOf(maxZ, node.z shr 4)
		}
		val m = ROUTE_NEIGHBORHOOD_SECTIONS
		val sectionHit = batch.sections.any {
			it.x in (minX - m)..(maxX + m) && it.y in (minY - m)..(maxY + m) && it.z in (minZ - m)..(maxZ + m)
		}
		return sectionHit || batch.chunks.any { it.x in (minX - m)..(maxX + m) && it.z in (minZ - m)..(maxZ + m) }
	}

	private companion object {
		const val QUIET_EXTENSION_INTERVAL_MILLIS = 250L

		const val SYNC_SLICE_STANCES = 150

		const val RESOLVE_SLICE_EXPANSIONS = 4_000
		const val ROUTE_NEIGHBORHOOD_SECTIONS = 2
	}
}
