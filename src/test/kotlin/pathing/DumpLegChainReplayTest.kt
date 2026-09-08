package pathing

import com.lambda.pathing.LegChain
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.search.SearchExhaustion
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.TrajectoryDiagnostic
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.session.RouteResolution
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.time.Duration
import pathing.ProbeScenarios.moveLibrary

/**
 * Replays a live dump as a compound walk-through route: the dump's start and goal are the
 * first leg, PATHING_DUMP_WAYPOINTS ("x,y,z;x,y,z;...") the legs after it. Reproduces the
 * leg-switch behaviour a single-leg replay cannot. Diagnostic output, not an assertion.
 */
class DumpLegChainReplayTest {
    @Test
    fun `replay a dump as a walk-through route`() {
        val dir = System.getenv("PATHING_DUMP_DIR") ?: return
        val file = System.getenv("PATHING_DUMP_FILE") ?: return
        val waypoints = (System.getenv("PATHING_DUMP_WAYPOINTS") ?: return).split(';').map { text ->
            val (x, y, z) = text.split(',').map { it.trim().toInt() }
            Stance(x, y, z)
        }
        val loaded = PlanDump.read(Path.of(dir, file))
        val environment = loaded.environment()
        val options = loaded.moveOptions
        // PATHING_DUMP_START "x,y,z" replaces the dump's start with a body standing still on that cell.
        val startOverride = System.getenv("PATHING_DUMP_START")?.split(',')?.map { it.trim().toInt() }?.let { (x, y, z) -> Stance(x, y, z) }
        val start = startOverride ?: loaded.start
        val firstGoal = if (startOverride != null) waypoints.first() else loaded.goal
        val legGoals = if (startOverride != null) waypoints.drop(1) else waypoints
        val initial = if (startOverride == null) loaded.initialState else com.lambda.pathing.physics.MovementSimulationState.synthetic(
            profile = loaded.profile,
            position = net.minecraft.util.math.Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = com.lambda.interaction.managers.rotating.Rotation(0.0, 0.0),
            velocity = net.minecraft.util.math.Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val first = CoarsePlanningState(environment, options, start, firstGoal)
        first.repairFrom(start, emptySet(), emptySet())
        check(first.planner.repair(Duration.INFINITE).converged)
        first.planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(first.planner.routePlan(0L))
        val field = first.planner.valueField()

        val legStarts = listOf(firstGoal) + legGoals.dropLast(1)
        val states = legGoals.indices.map { CoarsePlanningState(environment, options, legStarts[it], legGoals[it]) }
        val chain = LegChain(
            world = null, states = states, starts = legStarts,
            coarseExpansionBudget = 1_000_000, snapshotRevision = 0L, cancelled = { false },
            probe = SearchProbe.NONE, executor = Executor { it.run() },
            fieldExpansionTicks = 36.0, fieldExpansionBudget = Duration.INFINITE, fieldExpansionNodes = 20_000,
        )
        chain.begin(LegChain.Leg(first, route, field, RouteResolution(first), null))

        val tempo = System.getenv("PATHING_DUMP_TEMPO")?.toLong() ?: 83L
        val clock = VirtualSearchClock(microsPerExpansion = tempo)
        val executor = VirtualExecutor(clock)
        val exhaustions = ArrayList<SearchExhaustion>()
        var lastPublished: com.lambda.pathing.search.PublishedPath? = null
        val perStance = HashMap<Stance, HashMap<String, Int>>()
        val restarts = ArrayList<String>()
        val focus = System.getenv("PATHING_DUMP_FOCUS")?.split(',')?.map { it.trim().toInt() }?.let { (x, y, z) -> Stance(x, y, z) }
        val focusAttempts = ArrayList<String>()
        val probe = object : SearchProbe {
            override fun attempt(rollout: com.lambda.pathing.search.TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
                val s0 = rollout.initialState
                if (focus == null || Stance.of(s0.position, s0.onGround) != focus || focusAttempts.size >= 6) return
                val frames = rollout.frames
                val trace = frames.filterIndexed { i, _ -> i % 3 == 0 || i == frames.lastIndex }.joinToString(" ") { f ->
                    "f%d(%.2f,%.2f,%.2f v%.2f,%.2f,%.2f g=%s j=%s s=%s yaw=%.0f)".format(
                        f.index, f.state.position.x, f.state.position.y, f.state.position.z,
                        f.state.velocity.x, f.state.velocity.y, f.state.velocity.z,
                        if (f.state.onGround) "1" else "0", if (f.input.jump) "1" else "0", if (f.input.sprint) "1" else "0", f.state.rotation.yaw)
                }
                focusAttempts += "ATTEMPT from (%.2f,%.2f,%.2f) v=(%.3f,%.3f,%.3f) yaw=%.1f ground=%s -> %s [%d frames] %s".format(
                    s0.position.x, s0.position.y, s0.position.z, s0.velocity.x, s0.velocity.y, s0.velocity.z, s0.rotation.yaw, s0.onGround,
                    diagnostic ?: (if (certified) "stopped" else "completed"), frames.size, trace)
            }
            override fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {
                val key = "${action.movement}/${action::class.simpleName}/sprint=${action.sprint} -> ${diagnostic?.let { it::class.simpleName } ?: "ok"}"
                perStance.getOrPut(from) { HashMap() }.merge(key, 1, Int::plus)
            }
            override fun blocked(frame: Int, sectionX: Int, sectionY: Int, sectionZ: Int, capturable: Boolean, stance: Stance, movement: com.lambda.pathing.core.MovementId) {
                perStance.getOrPut(stance) { HashMap() }.merge("BLOCKED/$movement/section($sectionX,$sectionY,$sectionZ)", 1, Int::plus)
            }
            override fun restarted(moving: Boolean, seedElapsed: Int, executing: Int, expansions: Int, drops: Int, spent: Int) {
                restarts += "restart(moving=$moving seed=$seedElapsed exec=$executing exp=$expansions)"
            }
            override fun braked(tipElapsed: Int, executing: Int, open: Int, parked: Int, deepestElapsed: Int) {
                restarts += "braked(tip=$tipElapsed exec=$executing open=$open parked=$parked deepest=$deepestElapsed)"
            }
        }
        val result = TrajectoryPlanner.walkHorizon(
            route, first.planner, initial, loaded.profile, environment, loaded.searchConfig,
            cursorFrame = { executor.cursorFrame() }, publish = { path, _ -> lastPublished = path; executor.offer(path) },
            started = System.currentTimeMillis(), clock = clock, adoptedSequence = executor::adoptedSequence,
            finalGoal = (legGoals.lastOrNull() ?: firstGoal), field = field, onExhaustion = { exhaustions += it },
            fieldExpansionBudget = Duration.INFINITE, legs = chain, probe = probe,
            improvementBudget = System.getenv("PATHING_DUMP_IMPROVER")?.toInt() ?: 0,
        )
        perStance.entries.sortedByDescending { it.value.values.sum() }.take(14).forEach { (stance, hist) ->
            println("[legs-replay] expansions from $stance (${hist.values.sum()}): " +
                hist.entries.sortedByDescending { it.value }.take(8).joinToString("  ") { "${it.key}=${it.value}" })
        }
        println("[legs-replay] restarts/brakes: ${restarts.takeLast(12)}")
        lastPublished?.let { path ->
            val end = path.plan.frames.last().state
            println("[legs-replay] last published: frames=${path.plan.frames.size} end=${end.position} touches=${path.legTouches.map { it.waypoint to it.frame }} profile=${path.movementProfile()}")
            println("[legs-replay] last segments: " + path.plan.segments.takeLast(8).joinToString(" ") { seg ->
                val d = (seg as? com.lambda.pathing.search.PlanSegment.Move)?.decision
                "${d?.movement ?: "terminal"}->${d?.step}@${seg.startFrame}"
            })
            val legField = (states.lastOrNull() ?: first).planner.valueField()
            val endStance = Stance.of(end.position, end.onGround)
            for (cell in listOf(endStance, Stance(endStance.x, endStance.y + 1, endStance.z), Stance(endStance.x, endStance.y - 1, endStance.z))) {
                val steps = legField.steps(cell, 3, 4.0, null)
                println("[legs-replay] field at $cell: mapped=${legField.isMapped(cell)} guide=${legField.guide(cell)} steps=${steps.map { "${it.movement}->${it.to}(${"%.1f".format(it.lowerBoundTicks)}) bounce=${it.bounce} launch=${it.launch} standingStart=${it.standingStart}" }}")
            }
        }
        System.getenv("PATHING_DUMP_CELLS")?.split(';')?.forEach { text ->
            val (x, y, z) = text.split(',').map { it.trim().toInt() }
            val v = environment.voxel(x, y, z)
            println("[legs-replay] cell ($x,$y,$z): passable=${v.fullyPassable} standing=${v.standingSurface} bouncy=${v.bouncy} medium=${v.medium}")
        }
        focus?.let { f ->
            val hist = perStance[f] ?: emptyMap<String, Int>()
            println("[legs-replay] FOCUS $f (${hist.values.sum()}): " + hist.entries.sortedByDescending { it.value }.joinToString("  ") { "${it.key}=${it.value}" })
            focusAttempts.forEach { println("[legs-replay] $it") }
        }
        (result as? PathPlanResult.Planned)?.path?.plan?.let { plan ->
            val segments = plan.segments
            fun show(i: Int): String {
                val seg = segments[i]
                val d = (seg as? com.lambda.pathing.search.PlanSegment.Move)?.decision
                val extra = when (d) {
                    is TrajectoryDecision.RunUpLaunch -> " retreat=%.2f hop=%s".format(d.retreatAlong, d.hopAlong)
                    is TrajectoryDecision.Launch -> " delay=${d.delayFrames}"
                    is TrajectoryDecision.Heading -> " yaw=%.0f delay=%s".format(d.yaw, d.delayFrames)
                    else -> ""
                }
                val e = seg.entry.position; val x = seg.exit.position
                return "#%d@%d %s->%s%s (%.1f,%.1f,%.1f)->(%.1f,%.1f,%.1f) %df".format(
                    i, seg.startFrame, d?.movement ?: "terminal", d?.step, extra, e.x, e.y, e.z, x.x, x.y, x.z, seg.frameCount)
            }
            val interesting = segments.indices.filter { i ->
                val d = (segments[i] as? com.lambda.pathing.search.PlanSegment.Move)?.decision
                d != null && (d.movement == com.lambda.pathing.core.MovementId.LADDER_CATCH || d.movement == com.lambda.pathing.core.MovementId.CLIMB || d is TrajectoryDecision.RunUpLaunch)
            }
            val window = interesting.flatMap { (it - 4..it + 4) }.filter { it in segments.indices }.toSortedSet()
            var last = -2
            for (i in window) {
                if (i != last + 1) println("[legs-replay] ---")
                println("[legs-replay] ${show(i)}")
                last = i
                val seg = segments[i]
                if (seg.frameCount >= 20) {
                    for (f in seg.startFrame until seg.endFrame) {
                        val fr = plan.frames[f]; val p = fr.state.position
                        println("[legs-replay]      f%d (%.2f, %.2f, %.2f) v=(%.3f, %.3f, %.3f) ground=%s climb=%s in: fwd=%.0f jump=%s sprint=%s yaw=%.0f".format(
                            f, p.x, p.y, p.z, fr.state.velocity.x, fr.state.velocity.y, fr.state.velocity.z, fr.state.onGround,
                            environment.medium(kotlin.math.floor(p.x).toInt(), kotlin.math.floor(p.y).toInt(), kotlin.math.floor(p.z).toInt()) == com.lambda.pathing.world.Medium.CLIMBABLE,
                            fr.input.forward, fr.input.jump, fr.input.sprint, fr.input.rotation?.yaw ?: Double.NaN))
                    }
                }
            }
            run {
                var air = 0; var stand = 0; var slow = 0; var dwellTicks = 0; var jumps = 0; var groundedRun = 0; var sawAir = false
                var prev = plan.initialState
                val runs = ArrayList<Int>()
                for (fr in plan.frames) {
                    val st = fr.state; val sp = st.velocity.horizontalLength()
                    if (!st.onGround) { air++; if (prev.onGround) { jumps++; if (sawAir && groundedRun > 0) runs += groundedRun }; sawAir = true; groundedRun = 0 }
                    else { groundedRun++; if (sp <= 0.02) stand++ else if (sp < 0.25) slow++ }
                    prev = st
                }
                dwellTicks = runs.sumOf { it - 1 }
                println("[legs-replay] audit: frames=${plan.frames.size} jumps=$jumps air=$air dwell=$dwellTicks stand=$stand slow=$slow histogram=${runs.groupingBy { it }.eachCount().toSortedMap()}")
            }
        }
        val detail = when (result) {
            is PathPlanResult.Planned -> "PLANNED partial=${result.path.partial} frames=${result.path.plan.frames.size} touches=${result.path.legTouches.map { it.waypoint to it.frame }}"
            is PathPlanResult.Failed -> "FAILED ${result.failure.message}"
            else -> result::class.simpleName ?: "?"
        }
        println("[legs-replay] $file tempo=$tempo switches=${chain.switches} ledger=${chain.ledger}")
        println("[legs-replay] $detail")
        println("[legs-replay] last exhaustion: ${exhaustions.lastOrNull()}")
    }
}
