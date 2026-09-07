package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.WorldEventBatch

internal class ContinuousSyncPolicy(
    private val world: PathingWorld,
    private val coarseState: CoarsePlanningState,
    private val resolution: RouteResolution,
    private val field: CoarseValueField,
    private val start: Stance,
    private val finalGoal: Stance,
    private val snapshotRevision: Long,
    private val coarseExpansionBudget: Int,
    private val cancelled: () -> Boolean,
    private val probe: SearchProbe,
) : (CoarseRoutePlan) -> WorldSyncResult {
    private var lastQuietExtension = 0L

    private var syncNanos = 0L

    /** Search-thread time spent in coarse resynchronisation, for the exhaustion ledger. */
    val syncMillis: Long get() = syncNanos / 1_000_000L

    override fun invoke(current: CoarseRoutePlan): WorldSyncResult {
        val batch = world.drainEvents()
        if (batch.isEmpty) {
            // Finish a sliced resynchronisation before treating the world as quiet.
            val t = System.nanoTime()
            val more = coarseState.continueSync(SYNC_SLICE_STANCES)
            syncNanos += System.nanoTime() - t
            if (more) return WorldSyncResult.Woken
            // A horizon-truncated route never extends by itself when its terminal area
            // is already loaded -- no world event will ever arrive. Re-resolve
            // periodically until the route reaches the final goal.
            val extending = current.goal != finalGoal
            if (!extending) return WorldSyncResult.Quiet
            val now = System.currentTimeMillis()
            if (now - lastQuietExtension < QUIET_EXTENSION_INTERVAL_MILLIS) {
                return WorldSyncResult.Quiet
            }
            lastQuietExtension = now
            val resolveStarted = System.nanoTime()
            val next = resolution.resolve(
                start, snapshotRevision, coarseExpansionBudget,
            ) { cancelled() }
            syncNanos += System.nanoTime() - resolveStarted
            if (next == null || next.nodes == current.nodes) return WorldSyncResult.Quiet
            return WorldSyncResult.Changed(next)
        }

        val t = System.nanoTime()
        coarseState.applyEvents(batch, SYNC_SLICE_STANCES)
        syncNanos += System.nanoTime() - t
        val extending = current.goal != finalGoal
        val mutated = batch.mutations.isNotEmpty() || batch.chunks.isNotEmpty()

        if (!routeNeighborhoodTouched(current, batch) || (!extending && !mutated)) {
            return WorldSyncResult.Woken
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
            resolution.resolve(
                start, snapshotRevision, coarseExpansionBudget,
            ) { cancelled() }
        } else null
        syncNanos += System.nanoTime() - resolveStarted
        next?.goal?.let { terminal ->
            world.interestBlocks(
                terminal.x - 32, terminal.y - 16, terminal.z - 32,
                terminal.x + 32, terminal.y + 16, terminal.z + 32,
                InterestTier.CORRIDOR,
            )
        }
        return WorldSyncResult.Changed(next)
    }

    private fun routeNeighborhoodTouched(
        route: CoarseRoutePlan,
        batch: WorldEventBatch,
    ): Boolean {
        if (batch.sections.isEmpty() && batch.chunks.isEmpty()) return false
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
        route.nodes.forEach { node ->
            minX = minOf(minX, node.x shr 4); maxX = maxOf(maxX, node.x shr 4)
            minY = minOf(minY, node.y shr 4); maxY = maxOf(maxY, node.y shr 4)
            minZ = minOf(minZ, node.z shr 4); maxZ = maxOf(maxZ, node.z shr 4)
        }
        val m = ROUTE_NEIGHBORHOOD_SECTIONS
        val sectionHit = batch.sections.any {
            it.x in (minX - m)..(maxX + m) && it.y in (minY - m)..(maxY + m) && it.z in (minZ - m)..(maxZ + m)
        }
        if (sectionHit) return true
        return batch.chunks.any { it.x in (minX - m)..(maxX + m) && it.z in (minZ - m)..(maxZ + m) }
    }

    private companion object {
        const val QUIET_EXTENSION_INTERVAL_MILLIS = 250L

        /**
         * Graph nodes regenerated per sync call (~60 us each on rough terrain, so ~10 ms):
         * a full 9-chunk arrival on a field-sized graph is ~7k nodes, which blocked the
         * search for 300-400 ms at a time and collapsed its tempo window.
         */
        const val SYNC_SLICE_STANCES = 150
        const val ROUTE_NEIGHBORHOOD_SECTIONS = 2
    }
}
