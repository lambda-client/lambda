package com.lambda.pathing.coarse

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.world.CoarseVoxelView

/**
 * Stance-level memo for coarse edge generation, shared by everything in one planning
 * session that asks the move library for edges: both speed classes of the momentum
 * graph, successor and predecessor generation, route liveEdge checks and the value
 * field's steering reads. Before this cache each of those callers re-ran the full
 * template probe loop -- the same stance's edges were generated up to four times, and
 * the arc sweeps inside those probes are the dominant cost of building the lazy graph.
 *
 * The two directions share probes at list granularity: the edge o -> t sits in
 * edgesFrom(o) exactly when the inverted template probe in edgesTo(t) would find it,
 * so a cached list in either direction answers the other's per-template lookup without
 * re-probing. One asymmetry is honoured: edgesTo additionally requires the TARGET to
 * be a stance, so an empty incoming list recorded for a non-stance target is marked
 * and never trusted by the outgoing composition (a template may legally offer an edge
 * onto a cell that fails the stance test, e.g. outside the simulable Y range).
 *
 * Invalidation mirrors the library's read-set contract exactly: [invalidateVoxels] and
 * [invalidateChunks] evict what a change can reach ([SimpleMoveLibrary.affectedOrigins]
 * on the outgoing side, [SimpleMoveLibrary.affectedTargets] on the incoming side), and
 * [invalidateStances] drops explicit stances so a resynchronization pass regenerates
 * from the live view. Chunk eviction filters the cache's own keys by column range
 * rather than the graph's node set, because the cache can hold stances the graph never
 * adopted (value-field steering reads, rim origins).
 *
 * Not thread-safe, like every other structure in the coarse layer: one planning
 * session owns it.
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
