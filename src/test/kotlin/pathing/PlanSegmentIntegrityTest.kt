/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.trajectory.PlanSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * The segments must be the tape, exactly.
 *
 * Segments are only worth anything if they are a faithful decomposition of what the
 * executor actually replays: a gap or an overlap means a rejoin would splice against a
 * frame index that does not mean what it says. This checks the decomposition tiles the
 * tape contiguously, reproduces it input for input, and that every segment's recorded
 * entry and exit states are the certified frames at its boundaries.
 */
@Tag("bedrock-corpus")
class PlanSegmentIntegrityTest {

    @Test
    fun `segments tile the certified tape exactly`() {
        var checked = 0
        var worstState = 0.0
        for (scenario in ProbeScenarios.all()) {
            val path = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)?.path
                ?.takeIf { !it.partial } ?: continue
            val plan = path.plan
            val segments = plan.segments
            assertTrue(segments.isNotEmpty(), "${scenario.name}: certified plan carries no segments")

            assertEquals(0, segments.first().startFrame, "${scenario.name}: segments do not start at frame 0")
            segments.zipWithNext { previous, next ->
                assertEquals(
                    previous.endFrame, next.startFrame,
                    "${scenario.name}: segment gap or overlap at frame ${previous.endFrame}",
                )
            }
            assertEquals(
                plan.tape.frameCount, segments.last().endFrame,
                "${scenario.name}: segments do not reach the end of the tape",
            )

            assertEquals(
                plan.tape.asList(), segments.flatMap { it.inputs },
                "${scenario.name}: concatenated segment inputs are not the tape",
            )

            for (segment in segments) {
                val entry = if (segment.startFrame == 0) plan.initialState
                else plan.frames[segment.startFrame - 1].state
                val exit = plan.frames[segment.endFrame - 1].state
                worstState = maxOf(
                    worstState,
                    segment.entry.position.distanceTo(entry.position),
                    segment.exit.position.distanceTo(exit.position),
                )
            }
            checked++
        }
        assertTrue(checked > 0, "no scenario produced a full plan to check")
        println("[segments] %d plans checked, worst boundary state error %.3e".format(checked, worstState))
        assertTrue(worstState <= 1.0E-9, "segment boundary states drifted from the certified frames: $worstState")
    }

    @Test
    fun `report the decomposition`() {
        for (scenario in ProbeScenarios.all()) {
            val path = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)?.path
                ?.takeIf { !it.partial } ?: continue
            val plan = path.plan
            val moves = plan.segments.count { it is PlanSegment.Move }
            val terminals = plan.segments.count { it is PlanSegment.Terminal }
            val settled = plan.segments.count { it.settledExit(STOPPED_SPEED) }
            println(
                "[segments] %-18s frames=%-4d segments=%-3d (move %d, terminal %d)  settled rejoins=%d  median=%d frames".format(
                    scenario.name, plan.tape.frameCount, plan.segments.size, moves, terminals, settled,
                    plan.segments.map { it.frameCount }.sorted()[plan.segments.size / 2],
                ),
            )
        }
    }

    private companion object {
        const val STOPPED_SPEED = 0.01
    }
}
