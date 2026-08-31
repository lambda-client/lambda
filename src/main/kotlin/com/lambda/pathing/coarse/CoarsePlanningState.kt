package com.lambda.pathing.coarse

import com.lambda.pathing.changedChunkSet
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.time.Duration
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * The one-tick transition overhead is a fossil of the tape-per-edge architecture --
 * chained decisions stopped paying it, which is why tapes measure 0.78-0.84x of the
 * "admissible" bound -- but removing it is NOT free: swept 1.0/0.5/0.25/0.0, the
 * corpus improves monotonically (1254 to 1217) while the baseline walks trade course
 * wins for open-terrain collisions and stalls at every rung, and 0.25 even ends a
 * walk short. Cheaper walking re-ranks lines through the same velocity-blind guide
 * that blocks the momentum proposers; the honest re-price ships together with the
 * velocity-aware coarse layer, not before it.
 */
internal val DEFAULT_MOVE_COSTS: CoarseMoveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

internal class PlanningHorizonView(
    private val backing: CoarseVoxelView,
    private val granted: Set<PathingChunk>,
) : CoarseVoxelView {
    private fun grantedAt(x: Int, z: Int): Boolean = PathingChunk(x shr 4, z shr 4) in granted

    override val simulableStanceY: IntRange get() = backing.simulableStanceY

    override fun isKnown(x: Int, y: Int, z: Int): Boolean = grantedAt(x, z) && backing.isKnown(x, y, z)

    override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
        if (grantedAt(x, z)) backing.voxel(x, y, z) else CoarseVoxel.UNKNOWN

    override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? =
        if (grantedAt(x, z)) backing.collisionShape(x, y, z) else VoxelShapes.fullCube()

    override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass =
        if (grantedAt(x, z)) backing.collisionClass(x, y, z) else CollisionClass.FULL
}

internal class CoarsePlanningState(
    val snapshot: SnapshotSimulationEnvironment,
    moveOptions: SimpleMoveOptions,
    start: Stance,
    private val goal: Stance,
    val horizonChunks: Int = 0,
    private val frontierProbeRange: Int = 512,
    frontierSweepBudget: Int = 40_000,

    /** See [FrontierAnchors.NOTHING_CAPTURABLE]: capturable unknowns never anchor. */
    private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
) {
    private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

    private val grantedChunks = HashSet<PathingChunk>()

    private val view: CoarseVoxelView =
        if (horizonChunks <= 0) snapshot
        else PlanningHorizonView(snapshot, grantedChunks)

    /**
     * Sections whose capture lag suppressed an anchor: exactly the knowledge the
     * planner is stuck on. Primed at DEMAND tier by the route-resolution waits, so
     * refusing to guess always comes with asking to know.
     */
    private val captureLagSections = HashSet<Long>()

    private val collectCaptureLag: (Int, Int, Int) -> Unit = { x, y, z ->
        captureLagSections += net.minecraft.util.math.ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
    }

    init {
        grantChunksAround(start)
    }

    val planner = CoarsePlanner(
        view, moves, start, goal,
        sweepBudget = frontierSweepBudget, capturable = capturable,
        onCaptureLag = collectCaptureLag,
    )

    fun repairFrom(start: Stance, changed: Set<VoxelPos>, changedChunks: Set<PathingChunk>) {
        planner.updateStart(start)

        val revealed = grantChunksAround(start)
        if (changed.isNotEmpty()) planner.worldChanged(changed)
        val arrivals = changedChunks + revealed
        if (arrivals.isNotEmpty()) planner.chunksChanged(arrivals)
    }

    fun applyEvents(changedChunks: Set<PathingChunk>) {
        if (changedChunks.isNotEmpty()) planner.chunksChanged(changedChunks)
    }

    private fun advanceFrontierFrom(start: Stance): Boolean = planner.advanceFrontier(
        FrontierAnchors.probe(
            planner.view, moves, start, goal,
            maxSteps = frontierProbeRange, capturable = capturable,
            onCaptureLag = collectCaptureLag,
        ),
    )

    /** How the last route resolution spent its knowledge wait, for the startup ledger. */
    @Volatile
    var lastResolveReport: String = "no wait"
        private set

    /** Demand exactly the sections whose lag suppressed anchors, then forget them. */
    private fun demandCaptureLag(world: PathingWorld?) {
        if (world == null || captureLagSections.isEmpty()) return
        world.interest(ArrayList(captureLagSections), InterestTier.DEMAND)
        captureLagSections.clear()
    }

    fun resolveRoute(
        start: Stance,
        snapshotRevision: Long,
        maxExpansions: Int,
        world: PathingWorld? = null,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {

        var route = extractRoute(snapshotRevision, cancelled)
        if (route == null && advanceFrontierFrom(start)) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )
            route = extractRoute(snapshotRevision, cancelled)
        }
        // No route and no frontier can simply mean the snapshot has not caught up with
        // the client yet: anchors refuse capturable unknowns, so a cold start whose
        // surroundings are still being captured has neither. Wait for capture progress
        // and retry -- the alternative was optimistic micro-routes toward the body's
        // own uncaptured ring, retired by every capture batch: a random walk. The loop
        // is knowledge-driven, not wall-clock: any revision progress resets the stall
        // count, and sustained silence exits to the honest no-route failure.
        if (route == null && world != null) {
            var stalls = 0
            var rounds = 0
            var extracts = 0
            val waitStarted = System.nanoTime()
            // Stalls count TIMEOUTS only, and useful progress (a capture batch or a
            // frontier advance) resets them -- a chunk event with no captured content
            // wakes the wait without either, and such wakes must neither reset the
            // exit condition nor count toward it. The round cap bounds a pathological
            // spin on contentless churn.
            while (route == null && !cancelled() &&
                stalls < START_KNOWLEDGE_STALL_ROUNDS && rounds++ < START_KNOWLEDGE_MAX_ROUNDS
            ) {
                demandCaptureLag(world)
                if (!world.awaitEvents(world.revision, START_KNOWLEDGE_WAIT_MILLIS)) stalls++
                val batch = world.drainEvents()
                if (!batch.isEmpty) {
                    planner.chunksChanged(batch.changedChunkSet())
                }
                val advanced = advanceFrontierFrom(start)
                // A silent round cannot produce the route the last attempt failed to
                // find, and the attempt is not free: extractRoute re-checks the route
                // stances and may run the frontier sweep. Rounds are paced by capture
                // ticks, so paying that per round dominated the startup ledger's
                // route bucket on jump-heavy terrain.
                if (batch.isEmpty && !advanced) continue
                stalls = 0
                extracts++
                planner.repair(
                    timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
                )
                route = extractRoute(snapshotRevision, cancelled)
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
            if (DEBUG_RESOLVE) println("RESOLVE-DBG round=$rounds terminal=$terminal anchors=${planner.optimisticAnchors.size} granted=${grantedChunks.size}")

            world?.let { w ->
                // The lag sections reported by the frontier probes may lie off the
                // terminal's radius (a lateral column blocking a wide jump corridor);
                // demand them by name alongside the terminal neighborhood.
                demandCaptureLag(w)
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
            val revealed = grantChunksAround(terminal)
            if (revealed.isEmpty() && world == null) break
            if (revealed.isNotEmpty()) planner.chunksChanged(revealed)
            advanceFrontierFrom(start)
            planner.repair(
                timeBudget = Duration.INFINITE,
                maxExpansions = maxExpansions,
                cancelled = cancelled,
            )
            route = extractRoute(snapshotRevision, cancelled) ?: run {
                if (DEBUG_RESOLVE) println("RESOLVE-DBG round=$rounds EXTRACT NULL after granting around $terminal (revealed=${revealed.size}, anchors=${planner.optimisticAnchors.size}): ${planner.routeFailureReport()}")
                null
            } ?: return null
            if (DEBUG_RESOLVE) println("RESOLVE-DBG round=$rounds routed to ${route!!.goal}")
            if (route!!.goal == terminal) break
        }
        if (route.goal == goal && planner.retireAllAnchors()) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )

            route = extractRoute(snapshotRevision, cancelled) ?: route
        }
        return route
    }

    private fun extractRoute(snapshotRevision: Long, cancelled: () -> Boolean): CoarseRoutePlan? =
        planner.routePlan(snapshotRevision, cancelled = cancelled)
            ?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled)

            ?: run {
                if (planner.discoverReachableFrontier(cancelled)) {
                    planner.routePlan(snapshotRevision, cancelled = cancelled)
                } else null
            }

    private companion object {

        const val DEBUG_RESOLVE = false

        const val TERMINAL_GRANT_ROUNDS = 4

        const val TERMINAL_INTEREST_BLOCKS = 32
        const val TERMINAL_INTEREST_Y_BLOCKS = 16

        const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L

        /** Per-round wait for cold-start capture; useful progress resets the stalls. */
        const val START_KNOWLEDGE_WAIT_MILLIS = 200L

        /** Consecutive silent rounds before no-route is accepted as the true answer. */
        const val START_KNOWLEDGE_STALL_ROUNDS = 5

        /** Hard cap on wait rounds: contentless chunk-event wakes must not spin forever. */
        const val START_KNOWLEDGE_MAX_ROUNDS = 100
    }

    private fun grantChunksAround(start: Stance): Set<PathingChunk> {
        if (horizonChunks <= 0) return emptySet()
        val revealed = HashSet<PathingChunk>()
        val centerX = start.x shr 4
        val centerZ = start.z shr 4
        for (dx in -horizonChunks..horizonChunks) {
            for (dz in -horizonChunks..horizonChunks) {
                val chunk = PathingChunk(centerX + dx, centerZ + dz)
                if (grantedChunks.add(chunk)) revealed += chunk
            }
        }
        return revealed
    }
}
