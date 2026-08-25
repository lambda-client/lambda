package pathing

import com.lambda.pathing.CoarsePlanningState
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.ImmutableSnapshotSection
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.ChunkSectionPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Live-shaped repro: player far from an unstreamed goal on a large flat surface,
 * planning through CoarsePlanningState with the default planning horizon.
 */
class PlanningHorizonRegressionTest {
    private fun snapshot(streamedChunkRadius: Int, center: Stance): SnapshotSimulationEnvironment {
        val unavailableKeys = ConcurrentHashMap.newKeySet<Long>()
        val unavailable = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val floor = ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                set(x, y, z, if (y <= 4) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)
        val sections = HashMap<Long, ImmutableSnapshotSection>()
        val ccx = center.x shr 4
        val ccz = center.z shr 4
        for (cx in ccx - streamedChunkRadius..ccx + streamedChunkRadius) {
            for (cz in ccz - streamedChunkRadius..ccz + streamedChunkRadius) {
                sections[ChunkSectionPos.asLong(cx, 0, cz)] = floor
            }
        }
        return SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(-10_000, 0, -10_000, 10_000, 15, 10_000),
            sections = sections,
            defaultBlock = null,
            missingSection = { sx, sy, sz, exact ->
                check(!exact) { "exact simulation must never consume unstreamed terrain" }
                unavailableKeys += ChunkSectionPos.asLong(sx, sy, sz)
                unavailable
            },
            unavailableSectionKeys = unavailableKeys,
        )
    }

    private fun plan(horizonChunks: Int): Pair<CoarsePlanningState, com.lambda.pathing.coarse.CoarseRoutePlan?> {
        val start = Stance(27, 5, -34)
        val goal = Stance(-164, 5, -13)
        val state = CoarsePlanningState(
            snapshot = snapshot(streamedChunkRadius = 10, center = start),
            moveOptions = SimpleMoveOptions(),
            start = start,
            goal = goal,
            horizonChunks = horizonChunks,
        )
        state.repairFrom(start, emptySet(), emptySet())
        val result = state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        println("horizon=$horizonChunks expansions=${result.processedNodes} nodes=${state.planner.graphSize} converged=${result.converged}")
        val route = state.planner.routePlan(snapshotRevision = 1L)
            ?: state.planner.resynchronizedRoutePlan(snapshotRevision = 1L)
            ?: run {
                if (state.planner.discoverReachableFrontier()) state.planner.routePlan(snapshotRevision = 1L) else null
            }
        if (route == null) println("FAILURE horizon=$horizonChunks report: ${state.planner.routeFailureReport()}")
        return state to route
    }

    @Test
    fun `horizon off finds a route toward the unstreamed goal`() {
        val (_, route) = plan(horizonChunks = 0)
        assertNotNull(route)
    }

    @Test
    fun `default horizon finds a route toward the unstreamed goal`() {
        val (_, route) = plan(horizonChunks = 4)
        val r = assertNotNull(route, "no coarse route with the planning horizon enabled")
        assertTrue(r.nodes.first() == Stance(27, 5, -34))
    }
}

/**
 * The exact live failure of 2026-08-24: a journey created (and retained) while the
 * player stood at the goal is re-planned after the player teleported 280 blocks away.
 * The goal's terrain is granted and streamed, the middle is not.
 */
class RetainedJourneyReplanTest {
    @Test
    fun `re-planning a retained journey from outside streamed range still routes`() {
        val unavailableKeys = ConcurrentHashMap.newKeySet<Long>()
        val unavailable = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val floor = ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                set(x, y, z, if (y <= 4) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)
        val goal = Stance(-164, 5, -13)
        val firstStart = Stance(-162, 5, -12)
        val teleportedStart = Stance(27, 5, -34)
        // Streamed: 10 chunks around the goal area and 10 around the teleport target;
        // the middle never streamed (the player teleported across it).
        val sections = HashMap<Long, ImmutableSnapshotSection>()
        for (center in listOf(goal, teleportedStart)) {
            val ccx = center.x shr 4
            val ccz = center.z shr 4
            for (cx in ccx - 6..ccx + 6) for (cz in ccz - 6..ccz + 6) {
                sections[ChunkSectionPos.asLong(cx, 0, cz)] = floor
            }
        }
        val snapshot = SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(-10_000, 0, -10_000, 10_000, 15, 10_000),
            sections = sections,
            defaultBlock = null,
            missingSection = { sx, sy, sz, exact ->
                check(!exact) { "exact simulation must never consume unstreamed terrain" }
                unavailableKeys += ChunkSectionPos.asLong(sx, sy, sz)
                unavailable
            },
            unavailableSectionKeys = unavailableKeys,
        )
        val state = CoarsePlanningState(snapshot, SimpleMoveOptions(), firstStart, goal, horizonChunks = 4)
        state.repairFrom(firstStart, emptySet(), emptySet())
        state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        assertNotNull(state.planner.routePlan(snapshotRevision = 1L), "the near leg must route")

        // The player reaches the goal, then teleports away and asks again.
        state.repairFrom(teleportedStart, emptySet(), emptySet())
        val result = state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        println("replan expansions=${result.processedNodes} nodes=${state.planner.graphSize} converged=${result.converged}")
        val route = state.planner.routePlan(snapshotRevision = 2L)
            ?: state.planner.resynchronizedRoutePlan(snapshotRevision = 2L)
            ?: run {
                if (state.planner.discoverReachableFrontier()) state.planner.routePlan(snapshotRevision = 2L) else null
            }
        if (route == null) println("FAILURE report: ${state.planner.routeFailureReport()}")
        val r = assertNotNull(route, "no coarse route after teleporting away from a retained journey")
        assertTrue(r.nodes.first() == teleportedStart, "route must start at the body, got ${r.nodes.first()}")
    }
}
