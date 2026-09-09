package pathing

import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.LongLazyGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class DStarBudgetTest {
    private fun planner(start: Long) = LongDStarLite(
        LongLazyGraph(
            successorProvider = { node, sink -> if (node > 0) sink.add(node - 1, 1.0) },
            predecessorProvider = { node, sink -> if (node < 100) sink.add(node + 1, 1.0) },
        ), start, 0, { _, _ -> 0.0 },
    )

    @Test
    fun `cancellation wins over both limits and is polled every sixteen expansions`() {
        val planner = planner(100)
        val cancelled = planner.computeShortestPath(Duration.ZERO, 0) { true }
        assertTrue(cancelled.cancelled)
        assertFalse(cancelled.timedOut)
        assertFalse(cancelled.expansionLimitReached)
        var checks = 0
        val result = planner.computeShortestPath(Duration.INFINITE) { ++checks == 2 }
        assertEquals(16, result.processedNodes)
        assertEquals(2, checks)
        assertTrue(result.cancelled)
    }

    @Test
    fun `field expansion preserves cancellation cadence and limit priority`() {
        val planner = planner(0)
        assertTrue(planner.computeShortestPath(Duration.INFINITE).converged)
        val limited = planner.expandField(100.0, Duration.ZERO, 0)
        assertTrue(limited.expansionLimitReached)
        assertFalse(limited.timedOut)
        val timed = planner.expandField(100.0, Duration.ZERO)
        assertTrue(timed.timedOut)
        assertEquals(0, timed.processedNodes)
        var checks = 0
        val result = planner.expandField(100.0, Duration.INFINITE) { ++checks == 2 }
        assertEquals(16, result.processedNodes)
        assertEquals(2, checks)
        assertTrue(result.cancelled)
    }

    @Test
    fun `infinite start cost returns without field work but still checks cancellation`() {
        val planner = planner(100)
        var checks = 0
        val result = planner.expandField(100.0, Duration.ZERO, 0) { checks++; true }
        assertEquals(1, checks)
        assertEquals(0, result.processedNodes)
        assertTrue(result.cancelled)
        assertTrue(result.converged)
        assertFalse(result.timedOut)
        assertFalse(result.expansionLimitReached)
    }
}
