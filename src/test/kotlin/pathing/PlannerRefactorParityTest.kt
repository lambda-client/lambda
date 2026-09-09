package pathing

import com.lambda.pathing.PathPlanResult
import java.security.MessageDigest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Tag("bedrock-corpus")
class PlannerRefactorParityTest {
    @Test
    fun `refactoring preserves complete simulated trajectories`() {
        val rows = buildList {
            for (scenario in ProbeScenarios.all()) {
                for (budget in listOf(0, 128)) {
                    val outcome = ProbeScenarios.plan(scenario, improvementBudget = budget)
                    val path = assertIs<PathPlanResult.Planned>(outcome.result, scenario.name).path
                    val digest = MessageDigest.getInstance("SHA-256")
                    for (frame in path.plan.frames) {
                        digest.update("${frame.index}|${frame.input}|${frame.state}\n".toByteArray(Charsets.UTF_8))
                    }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    add("${scenario.name} budget=$budget partial=${path.partial} frames=${path.plan.frames.size} " +
                        "expansions=${outcome.exhaustions.lastOrNull()?.expansions ?: 0} sha256=$hash")
                }
            }
        }
        val actual = rows.joinToString("\n", postfix = "\n")
        println(actual)
        val recording = System.getProperty("lambda.pathing.refactorBaseline")
        if (recording != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(recording), actual)
        } else {
            val expected = checkNotNull(javaClass.getResourceAsStream("/pathing-refactor-parity.txt"))
                .bufferedReader().use { it.readText() }
            assertEquals(expected, actual)
        }
    }
}
