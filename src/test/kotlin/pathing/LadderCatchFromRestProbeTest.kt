package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.search.VirtualSearchClock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration
import pathing.ProbeScenarios.moveLibrary

/**
 * Ad hoc replay of the field ladder-catch dump (not committed; drop it into
 * build/run/clientGameTest/neolambda/pathing-dumps). The durable guard is
 * [SingleRungCatchTest]; this prints how far the real terrain gets.
 */
class LadderCatchFromRestProbeTest {
    @Test
    fun `the field ladder catch from the tape terminal`() {
        for (micros in listOf(640L, 40L)) {
            replay("plan-1788790509917.dump", com.lambda.pathing.core.Stance(-27, 69, -56), micros)
            replay("plan-1788790509757.dump", com.lambda.pathing.core.Stance(-17, 69, 20), micros)
        }
    }

    private fun replay(name: String, watch: com.lambda.pathing.core.Stance, microsPerExpansion: Long) {
        val path = Path.of("build/run/clientGameTest/neolambda/pathing-dumps/$name")
        if (!Files.exists(path)) return
        val loaded = PlanDump.read(path)
        val environment = loaded.environment()
        val moves = moveLibrary(loaded.moveOptions)
        val planner = CoarsePlanner(environment, moves, loaded.start, loaded.goal)
        planner.repair(Duration.INFINITE)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = planner.routePlan(0L) ?: run { println("[catch] NO COARSE ROUTE"); return }
        // Walked under the corpus tempo (a body consuming the tape), as the field does.
        val clock = VirtualSearchClock(microsPerExpansion = microsPerExpansion)
        val seen = HashMap<String, Int>()
        val byStance = java.util.TreeMap<Int, IntArray>() // route index -> [expansions, anchored]
        val routeIndex = route.nodes.withIndex().associate { (i, n) -> n to i }
        val probe = object : com.lambda.pathing.search.SearchProbe {
            override fun expansion(from: com.lambda.pathing.core.Stance, action: com.lambda.pathing.actions.TrajectoryDecision, diagnostic: com.lambda.pathing.search.TrajectoryDiagnostic?) {
                val idx = routeIndex[from] ?: -1
                val cell = byStance.getOrPut(idx) { IntArray(2) }
                cell[0]++
                if (diagnostic == null) cell[1]++
                if (idx in 12..16) {
                    val key = "$idx $from ${action.toString().substringBefore("(")}:${(action as? com.lambda.pathing.actions.TrajectoryDecision.Launch)?.step ?: (action as? com.lambda.pathing.actions.TrajectoryDecision.Walk)?.step ?: ""} -> " + (diagnostic?.toString()?.substringBefore("(") ?: "anchored")
                    seen.merge(key, 1, Int::plus)
                }
            }
            override fun restarted(moving: Boolean, seedElapsed: Int, executing: Int, expansions: Int, drops: Int, spent: Int) {
                println("[catch]   restart moving=$moving seed=$seedElapsed cursor=$executing expansions=$expansions drops=$drops")
            }
            override fun publishRefused(reason: () -> String, anchorElapsed: Int, tipElapsed: Int, executing: Int) {
                seen.merge("refused ${reason()}", 1, Int::plus)
            }
        }
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, loaded.initialState, loaded.profile, environment, loaded.searchConfig,
            cursorFrame = { clock.cursorFrame() }, publish = { _, _ -> }, started = System.currentTimeMillis(),
            clock = clock, probe = probe,
        )
        println("[catch]   expansions by route index: " + byStance.entries.joinToString(" ") { "${it.key}:${it.value[0]}/${it.value[1]}" })
        seen.entries.sortedByDescending { it.value }.take(30).forEach { (k, n) -> println("[catch]   x$n $k") }
        println("[catch] tempo=${microsPerExpansion}us $name " + when (outcome) {
            is PathPlanResult.Planned -> "PLANNED frames=${outcome.path.plan.frames.size}"
            is PathPlanResult.Failed -> "FAILED ${outcome.failure.message.take(900)}"
            else -> outcome::class.simpleName
        })
    }
}
