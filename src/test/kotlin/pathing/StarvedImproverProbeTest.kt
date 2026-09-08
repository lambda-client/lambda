package pathing

import com.lambda.pathing.PathPlanResult
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/**
 * A search starved of expansions per body frame commits early and leaves slack in its tape;
 * this is the offline stand-in for the field, where the improver's crossings do find
 * faster arrivals. Budget 0 against 1500 on independent seeds at a starved tempo.
 */
@Tag("bedrock-corpus")
class StarvedImproverProbeTest {
    @Test
    fun `improver recovers slack a starved search leaves`() {
        for (tempo in listOf(2500L)) {
            var plain = 0
            var improved = 0
            var splices = 0
            var arrivedPlain = 0
            var arrivedImproved = 0
            for (seed in 5..12) {
                val scenario = ProbeScenarios.parkour(seed)
                val off = ProbeScenarios.plan(scenario, microsPerExpansion = tempo)
                val on = ProbeScenarios.plan(scenario, microsPerExpansion = tempo, improvementBudget = 1500)
                val offPath = (off.result as? PathPlanResult.Planned)?.path?.takeIf { !it.partial }
                val onPath = (on.result as? PathPlanResult.Planned)?.path?.takeIf { !it.partial }
                if (offPath != null) { plain += offPath.plan.frames.size; arrivedPlain++ }
                if (onPath != null) { improved += onPath.plan.frames.size; arrivedImproved++ }
                val accepted = on.exhaustions.sumOf { it.improvementSplices }
                splices += accepted
                println(
                    "[starved] tempo=$tempo seed=$seed plain=${offPath?.plan?.frames?.size ?: "FAIL"} " +
                        "improved=${onPath?.plan?.frames?.size ?: "FAIL"} splices=$accepted " +
                        "last=${on.exhaustions.lastOrNull()?.improvementDiagnosis}",
                )
            }
            println("[starved] TOTAL tempo=$tempo plain=$plain ($arrivedPlain/8) improved=$improved ($arrivedImproved/8) splices=$splices")
        }
    }
}
