package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.TrajectoryDiagnostic
import java.nio.file.Path
import kotlin.test.Test

/**
 * Replays the field course dump (guarded: no-op without the local dump file) and
 * attributes search expansions. THE local reproduction of the publication-refusal
 * grind: three consecutive span-4 island jumps collect ~50k anchored-OK duplicate
 * anchors and ~43k frame-0..2 RepeatedCoarseStance fan attempts -- 120k expansions
 * for a 46-block course. Keep until the grind work lands; the quantified anatomy and
 * the MEASURED-DEAD interventions live in the handoff (UPDATE 14/15).
 *
 * Caveat this instrument taught us: cursorless, publicationCap and the horizon
 * freeze, so it attributes expansion ECONOMICS but cannot validate a grind fix --
 * outcome-quality judgment belongs to bedrockCorpus and FieldParkourProbeTest.
 */
class GrindReproScratchTest {
    @Test
    fun `full course expansion economics`() {
        val path = Path.of("run/neolambda/pathing-dumps/plan-1788179631046.dump")
        if (!java.nio.file.Files.exists(path)) return
        val loaded = PlanDump.read(path)
        val environment = loaded.environment()

        val state = CoarsePlanningState(
            snapshot = environment, moveOptions = loaded.moveOptions,
            start = loaded.start, goal = loaded.goal, horizonChunks = 4,
        )
        state.planner.repair(kotlin.time.Duration.INFINITE)
        val route = RouteResolution(state).resolve(loaded.start, 0L, 1_000_000) ?: return
        route.edges.forEach { println("ET route ${it.from} -> ${it.to} ${it.movement}") }

        val perStance = HashMap<Stance, Int>()
        val perMovement = HashMap<String, Int>()
        val hotDiags = HashMap<String, Int>()
        var total = 0
        val probe = object : SearchProbe {
            override fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {
                total++
                perStance.merge(from, 1, Int::plus)
                perMovement.merge(action.movement.toString(), 1, Int::plus)
                if (from.z in -39..-31 && from.y == 69) {
                    hotDiags.merge("${diagnostic?.toString()?.take(60) ?: "OK"}", 1, Int::plus)
                }
            }
        }
        val published = ArrayList<Pair<Int, String>>()
        val started = System.currentTimeMillis()
        val outcome = TrajectoryPlanner.walkHorizon(
            route, state.planner, loaded.initialState, loaded.profile, environment,
            loaded.searchConfig,
            cursorFrame = { null },
            publish = { p, _ ->
                val f = p.plan.frames
                published += f.size to
                    "t+%dms %df: %s -> %s".format(
                        System.currentTimeMillis() - started, f.size,
                        f.first().state.position.run { "(%.0f,%.0f,%.0f)".format(x, y, z) },
                        f.last().state.position.run { "(%.1f,%.1f,%.1f)".format(x, y, z) },
                    )
            },
            started = started,
            probe = probe,
            onExhaustion = { println("ET exhaustion $it") },
        )
        println("ET outcome=${outcome.javaClass.simpleName} wall=${System.currentTimeMillis() - started}ms total-expansions=$total")
        (outcome as? PathPlanResult.Planned)?.let { println("ET final=${it.path.plan.frames.size} frames") }
        published.forEach { println("ET pub ${it.second}") }
        println("ET by movement: ${perMovement.entries.sortedByDescending { it.value }}")
        perStance.entries.sortedByDescending { it.value }.take(6).forEach {
            println("ET hot stance ${it.key} = ${it.value} expansions")
        }
        hotDiags.entries.sortedByDescending { it.value }.take(8).forEach {
            println("ET hot diag ${it.value}x ${it.key}")
        }
    }
}
