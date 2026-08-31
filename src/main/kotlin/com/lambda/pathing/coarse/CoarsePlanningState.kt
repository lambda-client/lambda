package com.lambda.pathing.coarse

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
) {
    private val moves = SimpleMoveLibrary.build(costs = DEFAULT_MOVE_COSTS, options = moveOptions)

    private val grantedChunks = HashSet<PathingChunk>()

    private val view: CoarseVoxelView =
        if (horizonChunks <= 0) snapshot
        else PlanningHorizonView(snapshot, grantedChunks)

    init {
        grantChunksAround(start)
    }

    val planner = CoarsePlanner(view, moves, start, goal, sweepBudget = frontierSweepBudget)

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
        FrontierAnchors.probe(planner.view, moves, start, goal, maxSteps = frontierProbeRange),
    )

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
        if (route == null) return null
        var rounds = 0
        while (route!!.goal != goal && rounds++ < TERMINAL_GRANT_ROUNDS) {
            if (cancelled()) return route
            val terminal = route.goal

            world?.let { w ->
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
