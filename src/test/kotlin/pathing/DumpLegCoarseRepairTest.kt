package pathing

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration

/** Coarse repair of one leg over a live dump, with the production horizon, as the successor planner runs it. */
class DumpLegCoarseRepairTest {
    @Test
    fun `leg coarse repair converges under the planning horizon`() {
        val dir = System.getenv("PATHING_DUMP_DIR") ?: return
        val file = System.getenv("PATHING_DUMP_FILE") ?: return
        val legs = (System.getenv("PATHING_DUMP_LEG") ?: return).split(';').map { text ->
            val (x, y, z) = text.split(',').map { it.trim().toInt() }
            Stance(x, y, z)
        }
        val loaded = PlanDump.read(Path.of(dir, file))
        val environment = loaded.environment()
        for ((horizon, capturable) in listOf(0 to false, 4 to false, 4 to true)) {
            val state = CoarsePlanningState(
                environment, loaded.moveOptions, legs[0], legs[1], horizonChunks = horizon,
                capturable = if (capturable) { _, _ -> true } else com.lambda.pathing.coarse.FrontierAnchors.NOTHING_CAPTURABLE,
            )
            state.repairFrom(legs[0], emptySet(), emptySet())
            var started = System.nanoTime()
            var result = state.planner.repair(timeBudget = Duration.INFINITE, maxExpansions = 1_000_000)
            println(
                "[leg-coarse] horizon=$horizon capturable=$capturable ${legs[0]} -> ${legs[1]}: processed=${result.processedNodes} converged=${result.converged} " +
                    "graph=${state.planner.graphSize} ${(System.nanoTime() - started) / 1_000_000} ms report=${state.planner.routeFailureReport()}",
            )
            if (horizon > 0) {
                // The ring grants the walk would earn: reveal every chunk between start and goal, then repair again.
                val chunks = HashSet<com.lambda.pathing.core.PathingChunk>()
                for (cx in (minOf(legs[0].x, legs[1].x) shr 4)..(maxOf(legs[0].x, legs[1].x) shr 4))
                    for (cz in (minOf(legs[0].z, legs[1].z) shr 4)..(maxOf(legs[0].z, legs[1].z) shr 4))
                        chunks += com.lambda.pathing.core.PathingChunk(cx, cz)
                state.revealChunks(chunks)
                started = System.nanoTime()
                result = state.planner.repair(timeBudget = Duration.INFINITE, maxExpansions = 1_000_000)
                println(
                    "[leg-coarse]   after revealing ${chunks.size} chunks: processed=${result.processedNodes} converged=${result.converged} " +
                        "graph=${state.planner.graphSize} ${(System.nanoTime() - started) / 1_000_000} ms report=${state.planner.routeFailureReport()}",
                )
            }
        }
    }
}
