package pathing

import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.search.TrajectoryDiagnostic
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.VirtualSearchClock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.moveLibrary

/**
 * Optional local dump probe. PATHING_DUMP_DIR selects captured terrain; PATHING_DUMP_FILE
 * selects one filename, PATHING_DUMP_LIMIT the newest N, PATHING_DUMP_TEMPO the virtual
 * microseconds/expansion (default 83). Dump v5 lacks execution history: this is a new
 * simulated walk over captured terrain, not an exact reproduction of live timing.
 * Failures are diagnostic output, not assertions that all arbitrary dumps must solve.
 */
class LiveRefusalReplayTest {
    @Test
    fun `replay all live refusal dumps`() {
        val explicit = System.getenv("PATHING_DUMP_DIR")
        val dir = Path.of(explicit ?: "build/run/clientGameTest/neolambda/pathing-dumps")
        if (explicit != null) assertTrue(Files.isDirectory(dir), "dump directory missing: $dir")
        if (!Files.isDirectory(dir)) return
        val name = System.getenv("PATHING_DUMP_FILE")
        val limit = System.getenv("PATHING_DUMP_LIMIT")?.toLong() ?: Long.MAX_VALUE
        require(limit > 0)
        val dumps = Files.list(dir).use { paths ->
            paths.filter { it.toString().endsWith(".dump") && (name == null || it.fileName.toString() == name) }
                .sorted(Comparator.reverseOrder()).limit(limit).toList()
        }
        if (explicit != null) assertTrue(dumps.isNotEmpty(), "no matching dumps in $dir")
        for (p in dumps) replay(p)
    }

    private fun replay(p: Path) {
        val loaded = PlanDump.read(p)
        val environment = loaded.environment()
        val planner = CoarsePlanner(environment, moveLibrary(loaded.moveOptions), loaded.start, loaded.goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = planner.routePlan(0L)
        if (route == null) { println("[replay] ${p.fileName}: NO COARSE ROUTE"); return }
        val tempo = System.getenv("PATHING_DUMP_TEMPO")?.toLong() ?: 83L
        val clock = VirtualSearchClock(microsPerExpansion = tempo)
        val executor = VirtualExecutor(clock)
        val refusals = HashMap<String, Int>()
        val rootActions = LinkedHashMap<String, Int>()
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, loaded.initialState, loaded.profile, environment, loaded.searchConfig,
            cursorFrame = executor::cursorFrame, publish = { path, _ -> executor.offer(path) },
            adoptedSequence = executor::adoptedSequence, clock = clock,
            fieldExpansionBudget = Duration.INFINITE,
            started = System.currentTimeMillis(),
            probe = object : SearchProbe {
                override fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {
                    if (from == loaded.start) {
                        val key = "${action.movement} sprint=${action.sprint} -> ${diagnostic ?: "no rejection"}"
                        rootActions.merge(key, 1, Int::plus)
                    }
                }
                override fun publishRefused(reason: () -> String, anchorElapsed: Int, tipElapsed: Int, executing: Int) {
                    refusals.merge(reason(), 1, Int::plus)
                }
            },
        )
        if (outcome is PathPlanResult.Planned) {
            val simulator = MovementSimulator(loaded.profile, environment, loaded.initialState)
            var touchedGoal = false
            for (input in outcome.path.plan.tape.asList()) {
                assertTrue(simulator.tryTickMovement(input) is MovementSimulationStepResult.Advanced)
                val state = simulator.state
                touchedGoal = touchedGoal || (state.onGround && Stance.of(state.position, true) == loaded.goal)
            }
            if (!outcome.path.partial) {
                val end = simulator.state
                val error = kotlin.math.hypot(end.position.x - (loaded.goal.x + 0.5), end.position.z - (loaded.goal.z + 0.5))
                val arrived = error <= loaded.searchConfig.goalRadius ||
                    (loaded.searchConfig.touchArrival && touchedGoal && error <= loaded.searchConfig.touchRestRadius)
                assertTrue(arrived, "${p.fileName}: terminal error $error, touched=$touchedGoal")
                assertTrue(kotlin.math.abs(end.position.y - loaded.goal.y) <= 0.05)
                assertTrue(end.onGround && end.velocity.horizontalLength() <= loaded.searchConfig.stoppedSpeed)
            }
        }
        println("[replay] ${p.fileName} tempo=$tempo note=${loaded.note.take(600)}")
        println("[replay] root-actions=$rootActions")
        println("[replay] gate-refusals=" + refusals.entries.sortedByDescending { it.value }.take(12))
        println("[replay] executor adoptions=${executor.adoptions} refusals=${executor.refusals} reasons=${executor.refusalReasons}")
        val detail = when (outcome) {
            is PathPlanResult.Planned -> "PLANNED partial=${outcome.path.partial} frames=${outcome.path.plan.frames.size}"
            is PathPlanResult.Failed -> "FAILED ${outcome.failure.message}"
            else -> outcome::class.simpleName ?: "?"
        }
        println("[replay] ${p.fileName} start=${loaded.start} goal=${loaded.goal}: $detail")
    }
}
