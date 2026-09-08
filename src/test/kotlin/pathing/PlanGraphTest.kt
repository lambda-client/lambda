/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.search.PlanGraph
import com.lambda.pathing.search.PlanSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** The junction graph over a certified plan: one junction per segment boundary, frame 0 first. */
@Tag("bedrock-corpus")
class PlanGraphTest {

    private fun graphs(): List<Pair<String, PlanGraph>> = ProbeScenarios.all().mapNotNull { scenario ->
        val plan = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)
            ?.path?.takeIf { !it.partial }?.plan ?: return@mapNotNull null
        PlanGraph.of(plan)?.let { scenario.name to it }
    }

    @Test
    fun `junctions cover the spine`() {
        val graphs = graphs()
        assertTrue(graphs.isNotEmpty(), "no scenario produced a full plan")
        for ((name, graph) in graphs) {
            assertEquals(graph.spine.size + 1, graph.junctions.size, "$name: junction count")
            assertEquals(0, graph.junctions.first().frame, "$name: first junction is not frame 0")
            assertEquals(
                graph.frames, graph.junctions.last().frame,
                "$name: last junction is not the end of the tape",
            )
        }
    }

    @Test
    fun `rejoinable junctions include every settled one and honour the predicate`() {
        for (scenario in ProbeScenarios.all()) {
            val plan = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)
                ?.path?.takeIf { !it.partial }?.plan ?: continue
            val graph = PlanGraph.of(plan) ?: continue
            graph.junctions.forEach { junction ->
                if (junction.settled) assertTrue(junction.rejoinable, "${scenario.name}: settled J${junction.index} not rejoinable")
                if (junction.index > 0) {
                    assertEquals(junction.state.onGround, junction.rejoinable, "${scenario.name}: J${junction.index} predicate")
                }
            }
            // The start is always a legal cut.
            val none = PlanGraph.of(plan, rejoinable = { false })!!
            assertTrue(none.junctions.first().rejoinable, "${scenario.name}: start must stay a cut point")
            assertTrue(none.junctions.drop(1).none { it.rejoinable }, "${scenario.name}: predicate ignored")
        }
    }

    @Test
    fun `terminals are never offered as rejoin points`() {
        for ((name, graph) in graphs()) {
            graph.spine.forEachIndexed { index, segment ->
                if (segment is PlanSegment.Terminal && index == graph.spine.lastIndex) {
                    assertTrue(
                        !graph.junctions.last().settled,
                        "$name: the resting terminal was offered as a rejoin point",
                    )
                }
            }
        }
    }
}
