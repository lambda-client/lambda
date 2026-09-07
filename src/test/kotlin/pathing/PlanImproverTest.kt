/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.physics.MovementSimulator
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * The improver as the shipping planner actually runs it.
 *
 * Plans the corpus twice through the real planner -- budget off, then on -- and compares.
 * Measuring it in-place rather than over a finished plan is deliberate: the improver has
 * to respect the same reachability rule the publisher does, and an offline harness cannot
 * exercise that.
 *
 * The frame count is not taken on trust. Every improved tape is replayed input by input
 * from its own initial state and must arrive stopped, on the ground, inside the goal
 * radius -- because the first version of this "saved" eighteen frames by splicing away the
 * brake and ending two and a half blocks short at speed.
 */
@Tag("bedrock-corpus")
class PlanImproverTest {

    @Test
    fun `the improver shortens shipping plans without breaking them`() {
        var before = 0
        var after = 0
        var improved = 0
        var checked = 0

        for (scenario in ProbeScenarios.all()) {
            val plain = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)
                ?.path?.takeIf { !it.partial }?.plan ?: continue
            val outcome = ProbeScenarios.plan(scenario, improvementBudget = BUDGET)
            outcome.exhaustions.forEach { exhaustion ->
                if (exhaustion.improvementRollouts > 0) {
                    println(
                        "[improve]   %s: %d splices, %d frames, %d rollouts (%s)".format(
                            scenario.name, exhaustion.improvementSplices, exhaustion.improvementSaved,
                            exhaustion.improvementRollouts, exhaustion.improvementDiagnosis,
                        ),
                    )
                }
            }
            val spliced = (outcome.result as? PathPlanResult.Planned)
                ?.path?.takeIf { !it.partial }?.plan ?: continue
            checked++
            before += plain.tape.frameCount
            after += spliced.tape.frameCount
            if (spliced.tape.frameCount < plain.tape.frameCount) improved++

            println(
                "[improve] %-18s %d -> %d frames (%+.1f%%)  segments %d -> %d".format(
                    scenario.name, plain.tape.frameCount, spliced.tape.frameCount,
                    100.0 * (spliced.tape.frameCount - plain.tape.frameCount) / plain.tape.frameCount,
                    plain.segments.size, spliced.segments.size,
                ),
            )

            val simulator = MovementSimulator(
                profile = ProbeScenarios.PROFILE,
                environment = scenario.environment,
                initialState = spliced.initialState,
            )
            spliced.tape.asList().forEachIndexed { frame, input ->
                assertTrue(
                    simulator.tryTickMovement(input) is MovementSimulationStepResult.Advanced,
                    "${scenario.name}: improved tape was rejected at frame $frame",
                )
            }
            val end = simulator.state
            val error = hypot(
                end.position.x - (scenario.goal.x + 0.5), end.position.z - (scenario.goal.z + 0.5),
            )
            assertTrue(
                error <= CONSTRAINTS.goalRadius,
                "${scenario.name}: improved tape stopped %.2f blocks from the goal".format(error),
            )
            assertTrue(
                end.onGround && end.velocity.horizontalLength() <= CONSTRAINTS.stoppedSpeed,
                "${scenario.name}: improved tape does not end in a grounded stop",
            )
        }

        assertTrue(checked > 0, "no scenario produced a plan to improve")
        println(
            "[improve] TOTAL %d -> %d frames (%+.1f%%), %d/%d plans improved".format(
                before, after, 100.0 * (after - before) / before, improved, checked,
            ),
        )
        // The two sides are INDEPENDENT walks (budget changes search interleaving), so
        // walk variance of a frame or two per course rides on top of whatever the
        // improver did; the internal invariant -- improve() only returns strictly
        // shorter solutions -- is enforced by its score gate. One percent is the
        // measured variance band.
        assertTrue(after <= before * 1.01, "the improver made the corpus longer overall")
    }

    private companion object {
        val CONSTRAINTS = MotionConstraints()
        const val BUDGET = 1500
    }
}
