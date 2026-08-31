package pathing

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import java.nio.file.Path
import kotlin.test.Test

/** Temporary scratch: replay the no-coarse-route dump through the ring machinery. Delete after use. */
class NoRouteReplayScratchTest {
    @Test
    fun `replay the no-route dump`() {
        val path = Path.of("run/neolambda/pathing-dumps/plan-1788179631046.dump")
        if (!java.nio.file.Files.exists(path)) return
        val loaded = PlanDump.read(path)
        val environment = loaded.environment()
        println("ROUTE start=${loaded.start} goal=${loaded.goal} options=${loaded.moveOptions}")

        for (horizon in listOf(4)) {
            val state = CoarsePlanningState(
                snapshot = environment,
                moveOptions = loaded.moveOptions,
                start = loaded.start,
                goal = loaded.goal,
                horizonChunks = horizon,
            )
            val route = state.resolveRoute(
                start = loaded.start, snapshotRevision = 0L, maxExpansions = 1_000_000,
            )
            val planner = state.planner
            if (route != null) {
                println("ROUTE horizon=$horizon: ROUTED goal=${route.goal} ${route.edges.size} edges " +
                    route.edges.map { it.movement }.groupingBy { it }.eachCount())
            } else {
                // Where did the backward wave stop? The finite-g nodes' extents say.
                val nodes = planner.graphNodes
                val finite = nodes.filter { planner.stanceCost(it).isFinite() }
                println("ROUTE horizon=$horizon: NO ROUTE (${planner.routeFailureReport()})")
                println("ROUTE   nodes=${nodes.size} finite=${finite.size} anchors=${planner.optimisticAnchors.size}")
                fun extents(list: Collection<Stance>, label: String) {
                    if (list.isEmpty()) { println("ROUTE   $label: empty"); return }
                    println("ROUTE   $label: x=${list.minOf { it.x }}..${list.maxOf { it.x }} " +
                        "y=${list.minOf { it.y }}..${list.maxOf { it.y }} z=${list.minOf { it.z }}..${list.maxOf { it.z }}")
                }
                extents(nodes, "graph")
                extents(finite, "finite-g wave")
                // The wave boundary: finite nodes whose position is extremal toward the start.
                val startward = finite.sortedByDescending { it.z }.take(6)
                startward.forEach { println("ROUTE   wave edge node $it g=${planner.stanceCost(it)}") }
                planner.optimisticAnchors.forEach { (a, c) -> println("ROUTE   anchor $a cost=$c") }

                // Forward BFS from the start over the same (horizon) view: the set the
                // body can actually reach. Overlap with the finite wave = class bug;
                // no overlap = a real one-way gap between the two frontiers.
                val reachable = HashSet<Stance>()
                val queue = ArrayDeque(listOf(loaded.start))
                reachable += loaded.start
                while (queue.isNotEmpty() && reachable.size < 30_000) {
                    val s = queue.removeFirst()
                    for (edge in planner.moves.edgesFrom(planner.view, s)) {
                        if (reachable.add(edge.to)) queue += edge.to
                    }
                }
                val finiteSet = finite.toHashSet()
                val overlap = reachable.filter { it in finiteSet }
                println("ROUTE   forward-reachable=${reachable.size} overlap=${overlap.size}")
                overlap.take(8).forEach { println("ROUTE   OVERLAP $it g=${planner.stanceCost(it)}") }
                if (overlap.isEmpty()) {
                    var best: Triple<Stance, Stance, Int>? = null
                    for (r in reachable) for (f in finiteSet) {
                        val d = Math.abs(r.x - f.x) + Math.abs(r.y - f.y) + Math.abs(r.z - f.z)
                        if (best == null || d < best!!.third) best = Triple(r, f, d)
                    }
                    println("ROUTE   nearest pair: reachable=${best?.first} wave=${best?.second} manhattan=${best?.third}")
                    val re = reachable
                    println("ROUTE   reachable extents: x=${re.minOf { it.x }}..${re.maxOf { it.x }} y=${re.minOf { it.y }}..${re.maxOf { it.y }} z=${re.minOf { it.z }}..${re.maxOf { it.z }}")

                    // Forward edges that land INSIDE the finite wave: D* should have
                    // relaxed every one of these. Any hit names the broken relaxation.
                    var shown = 0
                    outer@ for (s in reachable) {
                        for (edge in planner.moves.edgesFrom(planner.view, s)) {
                            if (edge.to !in finiteSet) continue
                            println(
                                "ROUTE   CROSSING $s -[${edge.movement}]-> ${edge.to} " +
                                    "gFrom=${planner.stanceCost(s)} gTo=${planner.stanceCost(edge.to)}",
                            )
                            if (++shown >= 10) break@outer
                        }
                    }
                    if (shown == 0) println("ROUTE   no forward edge lands in the wave at all")

                    // The bouncy cells near the course, and the high launch stances.
                    for (x in -36..-20) for (y in 60..76) for (z in -35..5) {
                        if (planner.view.voxel(x, y, z).bouncy) {
                            println("ROUTE   SLIME at ($x,$y,$z) coveredBy=${planner.view.voxel(x, y + 1, z).standingSurface}")
                        }
                    }
                    reachable.filter { it.y >= 72 }.sortedBy { it.z }.take(10)
                        .forEach { println("ROUTE   high forward stance $it") }

                    // The field jump, probed directly: launch (-29,73,-21) over the
                    // single carpeted pad at (-29,66,-15) to the ledge at z=-10.
                    val jumpLaunch = Stance(-29, 73, -21)
                    val launchSupport = planner.view.voxel(-29, 72, -21)
                    println("ROUTE   launch support surface=${launchSupport.standingSurface} isStance=${planner.moves.isStance(planner.view, jumpLaunch)}")
                    for (dropTry in 4..7) {
                        for (span in 10..12) {
                            val probed = com.lambda.pathing.launch.BounceArcProbe.probe(
                                planner.view, jumpLaunch, dx = 0, dz = span, drop = dropTry, rise = -4,
                                launchHeight = jumpLaunch.y +
                                    planner.view.surfaceOffset(jumpLaunch.x, jumpLaunch.y - 1, jumpLaunch.z),
                            )
                            if (probed != null) {
                                val s = probed.solution
                                println("ROUTE   PROBE drop=$dropTry span=$span: jump=${s.jump} sprint=${s.sprint} hold=${s.holdTicks} contact=%.3f dist=%.3f".format(s.contactDistance, s.distance))
                            }
                        }
                    }
                    println("ROUTE   probe sweep done")

                    // The flight corridor: x=-29, launch z=-21 to landing z=-10.
                    for (y in 78 downTo 65) {
                        val row = (-22..-8).joinToString("") { z ->
                            val v = planner.view.voxel(-29, y, z)
                            when {
                                !planner.view.isKnown(-29, y, z) -> "?"
                                v.bouncy -> "B"
                                v.standingSurface != null && v.standingSurface!! < 0.999 -> "c"
                                v.standingSurface != null -> "#"
                                !v.fullyPassable -> "x"
                                else -> "."
                            }
                        }
                        println("ROUTE   corridor y=$y z=-22..-8: $row")
                    }

                    for (hr in listOf(Double.POSITIVE_INFINITY, 1.2)) {
                        val arc = com.lambda.pathing.launch.BallisticProfile.VANILLA.bounce(
                            0.0, 5.9375, -4.0, holdForward = true, sprint = true, jump = true,
                            holdTicks = 64, headroom = hr,
                        )
                        println("ROUTE   ARC headroom=$hr: dist=${arc?.distance} airTicks=${arc?.airTicks} apex=${arc?.apex}")
                    }

                    // Stage-by-stage: standing solves at the true depth, then the sweep.
                    val depth = 5.9375
                    val cache = com.lambda.pathing.launch.JumpArcProbe.SweepCellCache()
                    for ((j, sp) in com.lambda.pathing.launch.BounceSolver.STANDING_STYLES) {
                        val sol = com.lambda.pathing.launch.BounceSolver.solveStanding(
                            11.0, 5, -4, jump = j, sprint = sp,
                            contactDepth = depth, riseHeight = -4.0,
                            headroom = 1.2,
                        )
                        if (sol == null) {
                            println("ROUTE   STAGE jump=$j sprint=$sp: no standing solution")
                            continue
                        }
                        val reach = sol.launchOffset + sol.contactDistance
                        val clearance = com.lambda.pathing.launch.JumpArcProbe.sweepClearance(
                            planner.view, jumpLaunch, 0, 11, sol.arc,
                            launchOffset = sol.launchOffset, launchHeight = 73.0,
                            reads = null, cache = cache,
                        )
                        println(
                            "ROUTE   STAGE jump=$j sprint=$sp hold=${sol.holdTicks}: contact reach=%.3f (cell z=%d) dist=%.3f clearance=%s"
                                .format(reach, Math.floor(-20.5 + reach + 20.0).toInt() - 20, sol.distance, clearance?.toString() ?: "REFUSED"),
                        )
                    }
                    // And what the coarse library actually offers from the launch:
                    planner.moves.edgesFrom(planner.view, jumpLaunch).forEach {
                        println("ROUTE   launch edge ${it.movement} -> ${it.to}")
                    }

                    // Honest backward flood from the GOAL via edgesTo: where does true
                    // backward reachability die, and how close does it come to the
                    // forward set? That seam is the exact course spot to inspect.
                    val backward = HashSet<Stance>()
                    val bq = ArrayDeque(listOf(loaded.goal))
                    backward += loaded.goal
                    while (bq.isNotEmpty() && backward.size < 30_000) {
                        val s = bq.removeFirst()
                        for (edge in planner.moves.edgesTo(planner.view, s)) {
                            if (backward.add(edge.from)) bq += edge.from
                        }
                    }
                    println("ROUTE   backward-from-goal=${backward.size} extents: " +
                        "x=${backward.minOf { it.x }}..${backward.maxOf { it.x }} " +
                        "y=${backward.minOf { it.y }}..${backward.maxOf { it.y }} " +
                        "z=${backward.minOf { it.z }}..${backward.maxOf { it.z }}")
                    val meet = reachable.filter { it in backward }
                    println("ROUTE   forward/backward overlap=${meet.size}")
                    var seam: Triple<Stance, Stance, Int>? = null
                    if (meet.isEmpty()) {
                        for (r in reachable) for (b in backward) {
                            val d = Math.abs(r.x - b.x) + Math.abs(r.y - b.y) + Math.abs(r.z - b.z)
                            if (seam == null || d < seam!!.third) seam = Triple(r, b, d)
                        }
                        println("ROUTE   seam: forward=${seam?.first} backward=${seam?.second} manhattan=${seam?.third}")
                        seam?.let { (f, b, _) ->
                            for (probe in listOf(f, b)) {
                                for (y in probe.y - 3..probe.y + 1) {
                                    val row = (-3..3).joinToString("") { dz ->
                                        val v = planner.view.voxel(probe.x, y, probe.z + dz)
                                        when {
                                            !planner.view.isKnown(probe.x, y, probe.z + dz) -> "?"
                                            v.bouncy -> "B"
                                            v.standingSurface != null -> "#"
                                            !v.fullyPassable -> "x"
                                            else -> "."
                                        }
                                    }
                                    println("ROUTE   terrain@$probe y=$y z=${probe.z - 3}..${probe.z + 3}: $row")
                                }
                            }
                        }
                    } else {
                        meet.take(6).forEach { println("ROUTE   MEET $it g=${planner.stanceCost(it)}") }
                    }
                    seam?.let { (f, b, _) ->
                        for (z in -10..-7) for (y in 66..71) {
                            val v = planner.view.voxel(-12, y, z)
                            if (v.medium != com.lambda.pathing.world.Medium.AIR || v.standingSurface != null) {
                                println("ROUTE   cell (-12,$y,$z) medium=${v.medium} surface=${v.standingSurface} passable=${v.fullyPassable}")
                            }
                        }
                        println("ROUTE   edgesFrom(forward seam $f): " +
                            planner.moves.edgesFrom(planner.view, f).map { "${it.movement}->${it.to}" })
                        println("ROUTE   edgesTo(backward seam $b): " +
                            planner.moves.edgesTo(planner.view, b).map { "${it.from}-${it.movement}" })
                    }

                    // If the forward pocket borders unknown anywhere, the sweep should
                    // anchor there; a hermetically KNOWN pocket means the snapshot
                    // genuinely contains no crossing the vocabulary can walk.
                    val borders = reachable.filter {
                        com.lambda.pathing.coarse.FrontierAnchors.bordersUnknown(planner.view, it)
                    }
                    println("ROUTE   forward stances bordering unknown: ${borders.size}")
                    borders.take(10).forEach { println("ROUTE   border $it") }

                    // Cells captured as KNOWN but with unsupported physics (honey, web,
                    // powder snow, fluids) seal a pocket without minting anchors: the
                    // voxel reads UNKNOWN but the knowledge flag reads known.
                    val suspicious = ArrayList<Triple<Int, Int, Int>>()
                    for (x in -40..40) for (y in 66..76) for (z in -65..75) {
                        val v = planner.view.voxel(x, y, z)
                        if (v.medium == com.lambda.pathing.world.Medium.UNKNOWN &&
                            planner.view.isKnown(x, y, z)
                        ) {
                            suspicious += Triple(x, y, z)
                        }
                    }
                    println("ROUTE   known-but-unsupported cells: ${suspicious.size}")
                    suspicious.groupBy { Triple(it.first shr 3, it.second, it.third shr 3) }
                        .entries.take(14)
                        .forEach { (_, cells) -> println("ROUTE   unsupported cluster at ${cells.first()} size=${cells.size}") }
                }
            }
        }
    }
}
