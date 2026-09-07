package com.lambda.pathing.coarse

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.world.CoarseVoxelView

/**
 * Per-session memo of [SimpleMoveLibrary] edge lists, keyed by stance in both directions.
 * A cached list in either direction answers the other's per-template lookup, except that
 * an empty incoming list for a non-stance target is never reused by the outgoing side
 * (templates may offer edges onto cells that fail the stance test). Invalidation mirrors
 * the library's read sets: [SimpleMoveLibrary.affectedOrigins] outgoing,
 * [SimpleMoveLibrary.affectedTargets] incoming. Chunk eviction filters by column range,
 * not by graph membership, because the cache holds stances the graph never adopted.
 * Not thread-safe; one planning session owns it.
 */
internal class CoarseEdgeCache(
    private val view: CoarseVoxelView,
    private val moves: SimpleMoveLibrary,
) {
    private val outgoing = HashMap<Stance, List<CoarseEdge>>()
    private val incoming = HashMap<Stance, List<CoarseEdge>>()

    /** Targets whose incoming list is empty only because the target is not a stance. */
    private val nonStanceTargets = HashSet<Stance>()

    fun edgesFrom(origin: Stance): List<CoarseEdge> =
        outgoing.getOrPut(origin) { computeEdgesFrom(origin) }

    fun edgesTo(target: Stance): List<CoarseEdge> =
        incoming.getOrPut(target) { computeEdgesTo(target) }

    private fun computeEdgesFrom(origin: Stance): List<CoarseEdge> {
        if (!moves.isStance(view, origin)) return emptyList()
        return moves.templates.mapNotNull { template ->
            val target = origin.offset(template.dx, template.dy, template.dz)
            val known = if (target in nonStanceTargets) null else incoming[target]
            if (known != null) known.firstOrNull { it.id.template == template.id }
            else template.edge(view, origin)
        }
    }

    private fun computeEdgesTo(target: Stance): List<CoarseEdge> {
        if (!moves.isStance(view, target)) {
            nonStanceTargets += target
            return emptyList()
        }
        return moves.templates.mapNotNull { template ->
            val origin = target.offset(-template.dx, -template.dy, -template.dz)
            val known = outgoing[origin]
            if (known != null) known.firstOrNull { it.id.template == template.id }
            else if (moves.isStance(view, origin)) template.edge(view, origin) else null
        }
    }

    fun invalidateStances(stances: Iterable<Stance>) {
        for (stance in stances) {
            outgoing.remove(stance)
            incoming.remove(stance)
            nonStanceTargets.remove(stance)
        }
    }

    fun invalidateVoxels(changed: Iterable<VoxelPos>) {
        for (voxel in changed) {
            moves.affectedOrigins(voxel).forEach(outgoing::remove)
            for (target in moves.affectedTargets(voxel)) {
                incoming.remove(target)
                nonStanceTargets.remove(target)
            }
        }
    }

    fun invalidateChunks(chunks: Iterable<PathingChunk>) {
        val originRanges = chunks.map(moves::originColumnRanges)
        if (originRanges.isEmpty()) return
        val targetRanges = chunks.map(moves::targetColumnRanges)
        outgoing.keys.removeAll { stance ->
            originRanges.any { (x, z) -> stance.x in x && stance.z in z }
        }
        val touchesTarget = { stance: Stance ->
            targetRanges.any { (x, z) -> stance.x in x && stance.z in z }
        }
        incoming.keys.removeAll(touchesTarget)
        nonStanceTargets.removeAll(touchesTarget)
    }
}
