package pathing

// Replays live-captured refusal dumps (not committed; run ad hoc against
// build/run/clientGameTest/neolambda/pathing-dumps).
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.search.MotionPlanResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration
import pathing.ProbeScenarios.moveLibrary

class LiveRefusalReplayTest {
    @Test
    fun `replay all live refusal dumps`() {
        val dir = Path.of("build/run/clientGameTest/neolambda/pathing-dumps")
        if (!Files.isDirectory(dir)) return
        Files.list(dir).filter { it.toString().endsWith(".dump") }.sorted().forEach { p ->
            val loaded = PlanDump.read(p)
            val environment = loaded.environment()
            val moves = moveLibrary(loaded.moveOptions)
            val planner = CoarsePlanner(environment, moves, loaded.start, loaded.goal)
            planner.repair(Duration.INFINITE)
            planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
            val route = planner.routePlan(0L)
            if (route == null) { println("[replay] ${p.fileName}: NO COARSE ROUTE"); return@forEach }
            val outcome = TrajectoryPlanner.walkHorizon(
                route, planner, loaded.initialState, loaded.profile, environment,
                loaded.searchConfig,
                cursorFrame = { null }, publish = { _, _ -> },
                started = System.currentTimeMillis(),
            )
            println("[replay] ${p.fileName} options=${loaded.moveOptions} constraints=${loaded.searchConfig}")
            println("[replay] ${p.fileName} route(${route.nodes.size}): " + route.edges.joinToString(" ") { "${it.movement}->${it.to}" })
            println("[replay] ${p.fileName} note=${loaded.note?.take(600)}")
            val detail = when (outcome) {
                is MotionPlanResult.NoSafeStop ->
                    "NoSafeStop attempts=${outcome.attemptCount} nearest=${outcome.nearest?.let {
                        "err=%.3f speed=%.3f frames=%d diag=%s".format(
                            it.finalGoalError, it.finalHorizontalSpeed, it.simulatedFrames, it.diagnostic)
                    }}"
                is PathPlanResult.Planned -> "PLANNED frames=${outcome.path.plan.frames.size}"
                is PathPlanResult.Failed -> "FAILED ${outcome.failure.message}"
                else -> outcome::class.simpleName ?: "?"
            }
            println("[replay] ${p.fileName} start=${loaded.start} goal=${loaded.goal}: $detail")
        }
    }
}
