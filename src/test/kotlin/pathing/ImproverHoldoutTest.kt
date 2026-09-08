package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.physics.MovementSimulator
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Independent seeds at two pacing regimes, with actual terminal replay rather than count-only wins. */
@Tag("bedrock-corpus")
class ImproverHoldoutTest {
    @Test
    fun `independent parkour shortcuts arrive under both search tempos`() {
        val constraints = MotionConstraints()
        for (tempo in listOf(640L, 83L)) {
            var frames = 0
            var work = 0
            var splices = 0
            for (seed in 5..12) {
                val scenario = ProbeScenarios.parkour(seed)
                val outcome = ProbeScenarios.plan(
                    scenario, improvementBudget = 1500, microsPerExpansion = tempo,
                    parallelism = if (tempo == 83L) 4 else 1,
                )
                val path = assertNotNull((outcome.result as? PathPlanResult.Planned)?.path, scenario.name)
                assertFalse(path.partial, scenario.name)
                val plan = path.plan
                val simulator = MovementSimulator(ProbeScenarios.PROFILE, scenario.environment, plan.initialState)
                for (input in plan.tape.asList()) {
                    assertTrue(simulator.tryTickMovement(input) is MovementSimulationStepResult.Advanced, scenario.name)
                }
                val end = simulator.state
                assertTrue(end.onGround && end.velocity.horizontalLength() <= constraints.stoppedSpeed, scenario.name)
                val goalError = hypot(
                    end.position.x - (scenario.goal.x + 0.5), end.position.z - (scenario.goal.z + 0.5),
                )
                assertTrue(goalError <= constraints.goalRadius, scenario.name)
                assertTrue(abs(end.position.y - scenario.goal.y) <= 0.05, scenario.name)
                outcome.exhaustions.forEach { assertTrue(it.improvementRollouts <= 1500, scenario.name) }
                val attempts = outcome.exhaustions.sumOf { it.improvementRollouts }
                val accepted = outcome.exhaustions.sumOf { it.improvementSplices }
                frames += plan.frames.size
                work += attempts
                splices += accepted
                println("[improver-holdout] tempo=$tempo seed=$seed frames=${plan.frames.size} work=$attempts splices=$accepted")
            }
            println("[improver-holdout] TOTAL tempo=$tempo frames=$frames work=$work splices=$splices arrived=8/8")
        }
    }
}
