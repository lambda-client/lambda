package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.changedChunkSet
import kotlin.time.Duration

/**
 * Turns a [CoarsePlanningState] into a route the walk can start on, waiting on the world
 * where knowledge is what is missing: the cold-start knowledge wait, the terminal grant
 * rounds and the final anchor retirement. The coarse layer underneath stays synchronous;
 * every blocking call ([PathingWorld.awaitEvents]) lives here. Constants and their
 * rationale: docs/decisions/anchor-lifecycle.md.
 */
internal class RouteResolution(
    private val state: CoarsePlanningState,
) {
    private val planner get() = state.planner

    /** How the last resolution spent its knowledge wait, for the startup ledger. */
    @Volatile
    var lastResolveReport: String = "no wait"
        private set

    fun resolve(
        start: Stance,
        snapshotRevision: Long,
        maxExpansions: Int,
        world: PathingWorld? = null,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {
        val goal = state.goal

        var route = state.routePlan(snapshotRevision, cancelled)
        if (route == null && planner.advanceReachableFrontier(from = start)) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )
            route = state.routePlan(snapshotRevision, cancelled)
        }
        // Cold-start knowledge wait: no route and no frontier may mean the snapshot has
        // not caught up with the client. Knowledge-driven, not wall-clock: stalls count
        // timeouts only, real progress resets them, contentless wakes do neither.
        if (route == null && world != null) {
            var stalls = 0
            var rounds = 0
            var extracts = 0
            val waitStarted = System.nanoTime()
            while (route == null && !cancelled() &&
                stalls < START_KNOWLEDGE_STALL_ROUNDS && rounds++ < START_KNOWLEDGE_MAX_ROUNDS
            ) {
                state.demandCaptureLag(world)
                if (!world.awaitEvents(world.revision, START_KNOWLEDGE_WAIT_MILLIS)) stalls++
                val batch = world.drainEvents()
                if (!batch.isEmpty) {
                    planner.chunksChanged(batch.changedChunkSet())
                }
                val advanced = planner.advanceReachableFrontier(from = start)
                // A silent round cannot produce a new route; routePlan is not free.
                if (batch.isEmpty && !advanced) continue
                stalls = 0
                extracts++
                planner.repair(
                    timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
                )
                route = state.routePlan(snapshotRevision, cancelled)
            }
            lastResolveReport = "wait: %d rounds (%d extracts, %d stalls) in %d ms".format(
                rounds, extracts, stalls, (System.nanoTime() - waitStarted) / 1_000_000L,
            )
        }
        if (route == null) return null
        var rounds = 0
        while (route!!.goal != goal && rounds++ < TERMINAL_GRANT_ROUNDS) {
            if (cancelled()) return route
            val terminal = route.goal

            world?.let { w ->
                // Lag sections may lie off the terminal's radius; demand them by name too.
                state.demandCaptureLag(w)
                w.interestBlocks(
                    terminal.x - TERMINAL_INTEREST_BLOCKS, terminal.y - TERMINAL_INTEREST_Y_BLOCKS,
                    terminal.z - TERMINAL_INTEREST_BLOCKS,
                    terminal.x + TERMINAL_INTEREST_BLOCKS, terminal.y + TERMINAL_INTEREST_Y_BLOCKS,
                    terminal.z + TERMINAL_INTEREST_BLOCKS,
                    InterestTier.DEMAND,
                )
                var waited = 0L
                while (waited < TERMINAL_KNOWLEDGE_WAIT_MILLIS && !cancelled() && w.pendingDemand > 0) {
                    if (!w.awaitEvents(w.revision, 50)) break
                    waited += 50
                }
                val batch = w.drainEvents()
                if (!batch.isEmpty) {
                    planner.chunksChanged(batch.chunks + batch.sections.mapTo(HashSet()) { PathingChunk(it.x, it.z) })
                }
            }
            val revealed = state.grantChunksAround(terminal)
            if (revealed.isEmpty() && world == null) break
            if (revealed.isNotEmpty()) planner.chunksChanged(revealed)
            planner.advanceReachableFrontier(from = start)
            planner.repair(
                timeBudget = Duration.INFINITE,
                maxExpansions = maxExpansions,
                cancelled = cancelled,
            )
            route = state.routePlan(snapshotRevision, cancelled) ?: return null
            if (route.goal == terminal) break
        }
        if (route.goal == goal && planner.retireAllAnchors()) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )

            route = state.routePlan(snapshotRevision, cancelled) ?: route
        }
        return route
    }

    private companion object {

        const val TERMINAL_GRANT_ROUNDS = 4

        const val TERMINAL_INTEREST_BLOCKS = 32
        const val TERMINAL_INTEREST_Y_BLOCKS = 16

        const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L

        const val START_KNOWLEDGE_WAIT_MILLIS = 200L

        /** Consecutive timed-out rounds before no-route is accepted as the true answer. */
        const val START_KNOWLEDGE_STALL_ROUNDS = 5

        /** Hard cap on wait rounds; contentless wakes are neither progress nor stalls. */
        const val START_KNOWLEDGE_MAX_ROUNDS = 100
    }
}
