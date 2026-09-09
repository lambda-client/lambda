package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
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

	private val nonStanceTargets = LongOpenHashSet()

	private val completeOrigins = LongOpenHashSet()

	fun edgesFrom(node: Long): List<CoarseEdge> {
		val key = stanceKey(node)
		return outgoing.get(key) ?: run {
			val stance = PackedStance.stance(key)
			val edges = computeEdgesFrom(stance)
			outgoing.put(key, edges)
			if (!moves.readsUnknown(view, stance)) completeOrigins.add(key)
			edges
		}
	}

	fun isComplete(stance: Stance): Boolean = completeOrigins.contains(key(stance))

	fun edgesTo(node: Long): List<CoarseEdge> {
		val key = stanceKey(node)
		return incoming.get(key) ?: computeEdgesTo(PackedStance.stance(key)).also { incoming.put(key, it) }
	}

	fun edgesFrom(origin: Stance): List<CoarseEdge> = edgesFrom(key(origin))

	fun cachedEdgesFrom(origin: Stance): List<CoarseEdge>? = outgoing.get(key(origin))

	private fun computeEdgesFrom(origin: Stance): List<CoarseEdge> {
		if (!moves.isStance(view, origin)) return emptyList()
		return moves.templates.mapNotNull { template ->
			val target = origin.offset(template.dx, template.dy, template.dz)
			val targetKey = key(target)
			val known = if (nonStanceTargets.contains(targetKey)) null else incoming.get(targetKey)
			if (known != null) known.firstOrNull { it.id.template == template.id }
			else template.edge(view, origin)
		}
	}

	private fun computeEdgesTo(target: Stance): List<CoarseEdge> {
		if (!moves.isStance(view, target)) {
			nonStanceTargets.add(key(target))
			return emptyList()
		}
		return moves.templates.mapNotNull { template ->
			val origin = target.offset(-template.dx, -template.dy, -template.dz)
			val known = outgoing.get(key(origin))
			if (known != null) known.firstOrNull { it.id.template == template.id }
			else if (moves.isStance(view, origin)) template.edge(view, origin) else null
		}
	}

	fun invalidateStances(stances: Iterable<Stance>) {
		for (stance in stances) {
			val k = key(stance)
			outgoing.remove(k)
			completeOrigins.remove(k)
			incoming.remove(k)
			nonStanceTargets.remove(k)
		}
	}

	fun invalidateVoxels(changed: Iterable<VoxelPos>) {
		for (voxel in changed) {
			for (origin in moves.affectedOrigins(voxel)) {
				val k = key(origin)
				outgoing.remove(k)
				completeOrigins.remove(k)
			}
			for (target in moves.affectedTargets(voxel)) {
				val k = key(target)
				incoming.remove(k)
				nonStanceTargets.remove(k)
			}
		}
	}

	fun invalidateChunks(chunks: Iterable<PathingChunk>, arrivalsOnly: Boolean = false) {
		val originRanges = chunks.map(moves::originColumnRanges)
		if (originRanges.isEmpty()) return
		val targetRanges = chunks.map(moves::targetColumnRanges)
		removeColumns(outgoing.keys, originRanges, keep = if (arrivalsOnly) completeOrigins else null)
		if (!arrivalsOnly) removeColumns(completeOrigins, originRanges)
		removeColumns(incoming.keys, targetRanges)
		removeColumns(nonStanceTargets, targetRanges)
	}

	private fun removeColumns(keys: LongSet, ranges: List<Pair<IntRange, IntRange>>, keep: LongSet? = null) {
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val k = iterator.nextLong()
			if (keep != null && keep.contains(k)) continue
			val x = PackedStance.unpackX(k)
			val z = PackedStance.unpackZ(k)
			if (ranges.any { (xs, zs) -> x in xs && z in zs }) iterator.remove()
		}
	}

	private companion object {
		private const val SPEED_BIT = 1L

		fun stanceKey(node: Long): Long = node and SPEED_BIT.inv()

		fun key(stance: Stance): Long = PackedStance.pack(stance, SpeedClass.STOPPED)
	}
}
