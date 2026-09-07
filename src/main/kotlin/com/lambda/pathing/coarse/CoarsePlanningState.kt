package com.lambda.pathing.coarse

import com.lambda.pathing.world.changedChunkSet
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

/** Measured coarse costs; the 1.0-tick overhead is deliberate, see docs/decisions/transition-overhead.md. */
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
    frontierSweepBudget: Int = 40_000,

    /** See [FrontierAnchors.NOTHING_CAPTURABLE]: capturable unknowns never anchor. */
    private val capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
) {
    private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

    private val grantedChunks = HashSet<PathingChunk>()

    private val view: CoarseVoxelView =
        if (horizonChunks <= 0) snapshot
        else PlanningHorizonView(snapshot, grantedChunks)

    /** Sections whose capture lag suppressed an anchor; demanded before every wait round. */
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

    private fun advanceFrontierFrom(start: Stance): Boolean =
        planner.advanceReachableFrontier(from = start)

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
        // Cold-start knowledge wait: no route and no frontier may mean the snapshot has
        // not caught up with the client. Knowledge-driven, not wall-clock: stalls count
        // timeouts only, real progress resets them, contentless wakes do neither.
        // See docs/decisions/anchor-lifecycle.md.
        if (route == null && world != null) {
            var stalls = 0
            var rounds = 0
            var extracts = 0
            val waitStarted = System.nanoTime()
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
                // A silent round cannot produce a new route; extractRoute is not free.
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

            world?.let { w ->
                // Lag sections may lie off the terminal's radius; demand them by name too.
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
            route = extractRoute(snapshotRevision, cancelled) ?: return null
            if (route.goal == terminal) break
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

        const val TERMINAL_GRANT_ROUNDS = 4

        const val TERMINAL_INTEREST_BLOCKS = 32
        const val TERMINAL_INTEREST_Y_BLOCKS = 16

        const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L

        /** Cold-start knowledge wait; see docs/decisions/anchor-lifecycle.md. */
        const val START_KNOWLEDGE_WAIT_MILLIS = 200L

        /** Consecutive timed-out rounds before no-route is accepted as the true answer. */
        const val START_KNOWLEDGE_STALL_ROUNDS = 5

        /** Hard cap on wait rounds; contentless wakes are neither progress nor stalls. */
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
