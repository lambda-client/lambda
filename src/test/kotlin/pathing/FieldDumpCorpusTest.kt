package pathing

import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.search.VirtualSearchClock
import org.junit.jupiter.api.Tag
import pathing.ProbeScenarios.moveLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.useLines
import kotlin.test.Test
import kotlin.time.Duration

@Tag("field-dumps")
class FieldDumpCorpusTest {
    @Test
    fun `replay the newest dump of every field case`() {
        val directory = Path.of(System.getenv("PATHING_DUMP_DIR") ?: "run/neolambda/pathing-dumps")
        if (!Files.isDirectory(directory)) return println("[dumps] no dump directory at $directory")
        val match = System.getenv("PATHING_DUMP_MATCH")
        val cases = newestPerCase(directory).filter { match == null || it.name.contains(match) }
        var arrived = 0
        var frames = 0
        var unfinished = 0
        var noRoute = 0
        for (dump in cases) {
            val result = replay(dump)
            println("[dumps] ${dump.name} $result")
            when (result) {
                is Replay.Arrived -> { arrived++; frames += result.frames }
                is Replay.Unfinished -> unfinished++
                Replay.NoRoute -> noRoute++
            }
        }
        println("[dumps] cases=${cases.size} arrived=$arrived frames=$frames unfinished=$unfinished no-route=$noRoute")
    }

    private sealed interface Replay {
        data class Arrived(val frames: Int, val first: Int, val solutions: Int, val expansions: Long) : Replay {
            override fun toString() = "arrived frames=$frames first=$first solutions=$solutions expansions=$expansions"
        }

        data class Unfinished(val detail: String) : Replay {
            override fun toString() = "unfinished $detail"
        }

        data object NoRoute : Replay {
            override fun toString() = "no-route"
        }
    }

    private fun replay(dump: Path): Replay {
        val loaded = PlanDump.read(dump)
        val environment = loaded.environment()
        val planner = CoarsePlanner(environment, moveLibrary(loaded.moveOptions), loaded.start, loaded.goal)
        if (!planner.repair(maxExpansions = COARSE_EXPANSIONS).converged) return Replay.NoRoute
        planner.expandField(extraTicks = FIELD_TICKS, timeBudget = Duration.INFINITE, maxExpansions = FIELD_NODES)
        val route = planner.routePlan(0L) ?: return Replay.NoRoute
        val clock = VirtualSearchClock(microsPerExpansion = FIELD_MICROS_PER_EXPANSION)
        val executor = VirtualExecutor(clock)
        val exhaustions = ArrayList<SearchStats>()
        val outcome = walkHorizon(
            route, planner, loaded.initialState, loaded.profile, environment, loaded.searchConfig,
            execution = executor,
            publish = { path, _ -> executor.offer(path) },
            clock = clock,
            started = System.currentTimeMillis(),
            onExhaustion = { exhaustions += it },
            parallelism = FIELD_PARALLELISM,
        )
        val path = (outcome as? PathPlanResult.Planned)?.path
        val last = exhaustions.lastOrNull()
        if (path == null || path.partial) {
            return Replay.Unfinished((outcome as? PathPlanResult.Failed)?.reason ?: "partial")
        }
        return Replay.Arrived(
            frames = path.plan.frames.size,
            first = last?.firstSolutionFrames ?: -1,
            solutions = last?.solutionsFound ?: 0,
            expansions = exhaustions.sumOf { it.expansions.toLong() },
        )
    }

    private fun newestPerCase(directory: Path): List<Path> {
        val newest = LinkedHashMap<String, Path>()
        Files.list(directory).use { paths ->
            paths.filter { it.name.endsWith(".dump") }.sorted().forEach { dump ->
                val header = dump.useLines { lines -> lines.take(HEADER_LINES).toList() }
                if (header.firstOrNull() != "version ${PlanDump.VERSION}") return@forEach
                val start = header.firstOrNull { it.startsWith("start ") } ?: return@forEach
                val goal = header.firstOrNull { it.startsWith("goal ") } ?: return@forEach
                newest["$start|$goal"] = dump
            }
        }
        return newest.values.sorted()
    }

    private companion object {
        const val HEADER_LINES = 8
        const val COARSE_EXPANSIONS = 1_000_000
        const val FIELD_TICKS = 36.0
        const val FIELD_NODES = 20_000
        const val FIELD_MICROS_PER_EXPANSION = 83L
        const val FIELD_PARALLELISM = 4
    }
}
