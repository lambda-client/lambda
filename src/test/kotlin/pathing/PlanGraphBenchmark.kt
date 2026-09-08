package pathing

import com.lambda.pathing.search.PlanGraph
import kotlin.test.Test
import org.junit.jupiter.api.Tag
import kotlin.time.measureTime

/** Isolates graph bookkeeping from physics; run with bench --tests pathing.PlanGraphBenchmark. */
@Tag("bench")
class PlanGraphBenchmark {
    @Test
    fun benchmark() {
        val spine = graphFixture(256)
        var graph = spine
        repeat(1024) { index ->
            val from = index % 248
            graph = graph.with(PlanGraph.Alternate(from, from + 8, listOf(graphSegment(12), graphSegment(13))))
        }
        measure("spine-route", 5000) { spine.bestRoute().size }
        measure("alternate-route", 1000) { graph.bestRoute().size }
        measure("span-ranking", 1000) { spine.improvementTargets().size }
        for (size in listOf(32, 256)) {
            val fixture = graphFixture(size)
            measure("departures-reference-$size", 1000) { referenceDepartures(fixture).size }
            measure("departures-indexed-$size", 1000) { fixture.improvementDepartures().size }
        }
    }

    /** Exact former PlanImprover implementation, kept only as a benchmark reference. */
    private fun referenceDepartures(graph: PlanGraph): List<Int> {
        val seen = HashSet<Int>()
        val starts = ArrayList<Int>()
        for (span in graph.improvementTargets()) {
            if (span.from >= graph.junctions.lastIndex) continue
            if (seen.add(span.from)) starts += span.from
        }
        return starts
    }

    private fun measure(name: String, iterations: Int, operation: () -> Int) {
        // Tiered compilation can still finish during tiny fixed-count warmups. Warm
        // each operation for a full second before comparing the two implementations.
        val warmUntil = System.nanoTime() + 1_000_000_000L
        do {
            repeat(1000) { sink = operation() }
        } while (System.nanoTime() < warmUntil)
        val samples = List(5) {
            measureTime { repeat(iterations) { sink = operation() } }.inWholeNanoseconds.toDouble() / iterations / 1000
        }
        val bytes = PlannerBenchmark.allocatedBytes { repeat(iterations) { sink = operation() } } / iterations
        println("[graph-bench] $name median-us=${samples.sorted()[2]} bytes/op=$bytes samples=$samples")
    }

    companion object {
        @Volatile private var sink = 0
    }
}
