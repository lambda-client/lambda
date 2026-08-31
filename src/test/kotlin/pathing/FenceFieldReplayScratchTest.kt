package pathing

import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import java.nio.file.Path
import kotlin.test.Test

/** Temporary scratch: replay the fence-course no-route dump. Delete after use. */
class FenceFieldReplayScratchTest {
    @Test
    fun `replay the fence course dump`() {
        val path = Path.of("run/neolambda/pathing-dumps/plan-1788192764076.dump")
        if (!java.nio.file.Files.exists(path)) return
        val loaded = PlanDump.read(path)
        println("FF start=${loaded.start} goal=${loaded.goal} options=${loaded.moveOptions}")

        val state = CoarsePlanningState(
            snapshot = loaded.environment(),
            moveOptions = loaded.moveOptions,
            start = loaded.start,
            goal = loaded.goal,
            horizonChunks = 4,
        )
        val route = state.resolveRoute(start = loaded.start, snapshotRevision = 0L, maxExpansions = 1_000_000)
        val planner = state.planner
        if (route != null) {
            println("FF ROUTED ${route.edges.size} edges: " +
                route.edges.map { it.movement }.groupingBy { it }.eachCount())
            route.nodes.forEach { println("FF   node $it") }
            return
        }
        println("FF NO ROUTE (${planner.routeFailureReport()})")

        // Forward BFS from the start and backward flood from the goal; the seam
        // between the two sets is the course spot the vocabulary cannot cross.
        fun flood(seed: Stance, forward: Boolean): HashSet<Stance> {
            val seen = HashSet<Stance>()
            val queue = ArrayDeque(listOf(seed))
            seen += seed
            while (queue.isNotEmpty() && seen.size < 40_000) {
                val s = queue.removeFirst()
                val next = if (forward) planner.moves.edgesFrom(planner.view, s).map { it.to }
                else planner.moves.edgesTo(planner.view, s).map { it.from }
                for (n in next) if (seen.add(n)) queue += n
            }
            return seen
        }
        val forward = flood(loaded.start, forward = true)
        val backward = flood(loaded.goal, forward = false)
        println("FF forward=${forward.size} backward=${backward.size} overlap=${forward.count { it in backward }}")
        println("FF forward z extents: ${forward.minOf { it.z }}..${forward.maxOf { it.z }}")
        println("FF backward z extents: ${backward.minOf { it.z }}..${backward.maxOf { it.z }}")

        var seam: Triple<Stance, Stance, Int>? = null
        for (f in forward) for (b in backward) {
            val d = Math.abs(f.x - b.x) + Math.abs(f.y - b.y) + Math.abs(f.z - b.z)
            if (seam == null || d < seam!!.third) seam = Triple(f, b, d)
        }
        val (f, b, d) = seam ?: return
        println("FF seam forward=$f backward=$b manhattan=$d")
        println("FF edgesFrom(forward seam): " +
            planner.moves.edgesFrom(planner.view, f).map { "${it.movement}->${it.to}" })

        // Terrain slice through the seam along z (course axis), marking intrusions.
        for (x in listOf(f.x - 1, f.x, f.x + 1)) {
            println("FF terrain slice x=$x")
            for (y in (minOf(f.y, b.y) + 3) downTo (minOf(f.y, b.y) - 4)) {
                val z0 = minOf(f.z, b.z) - 3
                val z1 = maxOf(f.z, b.z) + 3
                val row = (z0..z1).joinToString("") { z ->
                    val v = planner.view.voxel(x, y, z)
                    when {
                        !planner.view.isKnown(x, y, z) -> "?"
                        v.bouncy -> "B"
                        v.intrudesAbove -> "f"
                        v.standingSurface != null && v.standingSurface!! < 0.999 -> "c"
                        v.standingSurface != null -> "#"
                        !v.fullyPassable -> "x"
                        else -> "."
                    }
                }
                println("FF   y=$y z=$z0..$z1: $row")
            }
        }
    }
}
