package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.TrajectoryDiagnostic
import com.lambda.pathing.search.TrajectoryRollout
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.time.measureTime

/** Counts actual rollout work, independently of the improver's accounting conventions. */
@Tag("bench")
class ImproverWorkBenchmark {
    private class Work : SearchProbe {
        var attempts = 0
        var frames = 0L
        override fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
            attempts++
            frames += rollout.frames.size
        }
    }

    @Test
    fun benchmark() {
        var totalAttempts = 0
        var totalSimulated = 0L
        var totalCertified = 0
        for (scenario in ProbeScenarios.all()) {
            ProbeScenarios.plan(scenario, improvementBudget = 1500)
            val counts = ArrayList<Triple<Int, Long, Int>>()
            val millis = List(3) {
                val work = Work()
                measureTime {
                    val outcome = ProbeScenarios.plan(scenario, improvementBudget = 1500, probe = work)
                    val path = assertNotNull((outcome.result as? PathPlanResult.Planned)?.path)
                    assertFalse(path.partial, scenario.name)
                    counts += Triple(work.attempts, work.frames, path.plan.frames.size)
                }.inWholeMicroseconds / 1000.0
            }
            counts.forEach { assertEquals(counts.first(), it, "${scenario.name}: work must be deterministic") }
            val (attempts, simulated, certified) = counts.first()
            totalAttempts += attempts
            totalSimulated += simulated
            totalCertified += certified
            println("[improver-work] ${scenario.name} attempts=$attempts simulated=$simulated certified=$certified median-ms=${millis.sorted()[1]} samples=$millis")
        }
        println("[improver-work] TOTAL attempts=$totalAttempts simulated=$totalSimulated certified=$totalCertified")
    }
}
