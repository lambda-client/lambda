package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet

internal class CoarseEdgeCache(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
) {
	private val outgoing = Long2ObjectOpenHashMap<List<CoarseEdge>>()
	private val incoming = Long2ObjectOpenHashMap<List<CoarseEdge>>()

	private val completeOrigins = LongOpenHashSet()

	private val standable = StanceMemo(view, moves)

	fun edgesFrom(node: Long): List<CoarseEdge> {
		val key = PackedStance.stanceKey(node)
		return outgoing.get(key) ?: run {
			val stance = PackedStance.stance(key)
			val complete = !moves.readsUnknown(view, stance)
			val edges = computeEdgesFrom(stance)
			outgoing.put(key, edges)
			if (complete) completeOrigins.add(key)
			edges
		}
	}

	fun isComplete(node: Long): Boolean = completeOrigins.contains(PackedStance.stanceKey(node))

	fun isComplete(origin: Stance): Boolean = completeOrigins.contains(key(origin))

	fun edgesTo(node: Long): List<CoarseEdge> {
		val key = PackedStance.stanceKey(node)
		return incoming.get(key) ?: computeEdgesTo(PackedStance.stance(key)).also { incoming.put(key, it) }
	}

	fun edgesFrom(origin: Stance): List<CoarseEdge> = edgesFrom(key(origin))

	fun cachedEdgesFrom(origin: Stance): List<CoarseEdge>? = outgoing.get(key(origin))

	private fun computeEdgesFrom(origin: Stance): List<CoarseEdge> {
		if (!standable.isStance(origin)) return emptyList()
		return moves.templates(view, origin).mapNotNull { template ->
			val known = incoming.get(key(origin.offset(template.dx, template.dy, template.dz)))
			edgeVia(known.takeUnless { it === NON_STANCE }, template, origin)
		}
	}

	private fun computeEdgesTo(target: Stance): List<CoarseEdge> {
		if (!standable.isStance(target)) return NON_STANCE
		return moves.templates(view, target).mapNotNull { template ->
			val origin = target.offset(-template.dx, -template.dy, -template.dz)
			if (!standable.isStance(origin)) return@mapNotNull null
			edgeVia(outgoing.get(key(origin)), template, origin)
		}
	}

	private fun edgeVia(known: List<CoarseEdge>?, template: MotionTemplate, origin: Stance): CoarseEdge? =
		if (known != null) known.firstOrNull { it.template == template.id } else template.edge(view, origin)

	fun invalidateStances(stances: Iterable<Stance>) {
		for (stance in stances) {
			val k = key(stance)
			outgoing.remove(k)
			completeOrigins.remove(k)
			incoming.remove(k)
			standable.remove(stance)
		}
	}

	fun invalidateChunks(chunks: Iterable<PathingChunk>, originRanges: List<Pair<IntRange, IntRange>>, arrivalsOnly: Boolean) {
		if (originRanges.isEmpty()) return
		val targetRanges = chunks.map(moves::targetColumnRanges)
		removeColumns(outgoing.keys, originRanges, keep = if (arrivalsOnly) completeOrigins else null)
		if (!arrivalsOnly) removeColumns(completeOrigins, originRanges)
		removeColumns(incoming.keys, targetRanges)
		standable.removeColumns(targetRanges)
	}

	private fun removeColumns(keys: LongSet, ranges: List<Pair<IntRange, IntRange>>, keep: LongSet? = null) {
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val k = iterator.nextLong()
			if (keep != null && keep.contains(k)) continue
			if (covers(ranges, k)) iterator.remove()
		}
	}

	companion object {
		private val NON_STANCE: List<CoarseEdge> = ArrayList(0)

		private fun key(stance: Stance): Long = PackedStance.pack(stance, SpeedClass.STOPPED)

		fun covers(ranges: List<Pair<IntRange, IntRange>>, key: Long): Boolean {
			val x = PackedStance.unpackX(key)
			val z = PackedStance.unpackZ(key)
			return ranges.any { (xs, zs) -> x in xs && z in zs }
		}
	}
}
